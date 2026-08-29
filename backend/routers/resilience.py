from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from sqlalchemy import desc

from database import get_db
from auth import CurrentUser
import models
import schemas
from services.resilience_manager import resilience_manager

router = APIRouter(prefix="/resilience", tags=["Blackout Resilience & Recovery"])


@router.get("/status", response_model=schemas.SystemStatusResponse)
def get_system_status(
    user_id: CurrentUser,
    db: Session = Depends(get_db)
):
    """
    Returns live system status, blackout mode state, last verified checkpoint,
    and journal transaction counts.
    """
    return resilience_manager.get_system_status(user_id=user_id, db=db)


@router.post("/checkpoint", response_model=schemas.SystemCheckpointResponse, status_code=status.HTTP_201_CREATED)
def create_checkpoint(
    user_id: CurrentUser,
    checkpoint_type: str = "MANUAL",
    db: Session = Depends(get_db)
):
    """
    Creates a full atomic snapshot of current products, stock, and bills.
    """
    checkpoint = resilience_manager.create_checkpoint(
        user_id=user_id,
        db=db,
        checkpoint_type=checkpoint_type
    )
    return checkpoint


@router.post("/simulate-blackout", response_model=schemas.SystemStatusResponse)
def simulate_blackout(
    user_id: CurrentUser,
    reason: Optional[str] = "Primary Database Unavailable / Corrupted",
    db: Session = Depends(get_db)
):
    """
    Simulates a primary database blackout / corruption event.
    Automatically captures a LAST_KNOWN_GOOD_CHECKPOINT if none exists and activates recovery mode.
    """
    return resilience_manager.simulate_blackout(
        user_id=user_id,
        db=db,
        reason=reason or "Primary Database Unavailable / Corrupted"
    )


@router.post("/restore", response_model=schemas.RecoveryReport)
def restore_system(
    user_id: CurrentUser,
    db: Session = Depends(get_db)
):
    """
    Executes RESTORE SYSTEM:
    1. Loads last known good checkpoint.
    2. Reads and replays all journaled transactions chronologically.
    3. Prevents duplicate bills / returns with idempotency checks.
    4. Marks replayed transactions as RECOVERED and returns detailed Recovery Report.
    """
    return resilience_manager.restore_system(user_id=user_id, db=db)


@router.post("/reset-demo", response_model=schemas.SystemStatusResponse)
def reset_demo(
    user_id: CurrentUser,
    db: Session = Depends(get_db)
):
    """
    Resets the Blackout Challenge demo baseline with standard products and stock.
    """
    return resilience_manager.reset_demo(user_id=user_id, db=db)


@router.get("/journal", response_model=List[schemas.JournalTransactionResponse])
def get_transaction_journal(
    user_id: CurrentUser,
    limit: int = 50,
    offset: int = 0,
    db: Session = Depends(get_db)
):
    """
    Fetches the append-oriented transaction journal for the current user.
    """
    transactions = db.query(models.JournalTransaction).filter(
        models.JournalTransaction.user_id == user_id
    ).order_by(desc(models.JournalTransaction.created_at)).offset(offset).limit(limit).all()
    return transactions


@router.post("/sync-journal", status_code=200)
def sync_journal_from_mobile(
    payload: dict,
    user_id: CurrentUser,
    db: Session = Depends(get_db)
):
    """
    Accepts a batch of journal transactions from the mobile app after recovery.
    Upserts them into the backend JournalTransaction table (idempotent by transaction_id).
    Also updates product stock to match the recovered state.
    """
    transactions = payload.get("transactions", [])
    upserted = 0
    skipped = 0

    for tx in transactions:
        tx_id = tx.get("transactionId") or tx.get("transaction_id") or ""
        existing = db.query(models.JournalTransaction).filter(
            models.JournalTransaction.transaction_id == tx_id,
            models.JournalTransaction.user_id == user_id
        ).first() if tx_id else None

        if existing:
            # Update status to RECOVERED
            existing.status = "RECOVERED"
            db.add(existing)
            skipped += 1
        else:
            new_tx = models.JournalTransaction(
                id=tx.get("id") or str(__import__("uuid").uuid4()),
                transaction_id=tx_id or str(__import__("uuid").uuid4()),
                user_id=user_id,
                type=tx.get("type", "SALE"),
                bill_id=tx.get("billId") or tx.get("bill_id"),
                product_id=tx.get("productId") or tx.get("product_id"),
                product_name=tx.get("productName") or tx.get("product_name", ""),
                quantity=int(tx.get("quantity", 0)),
                unit_price=float(tx.get("unitPrice") or tx.get("unit_price") or 0),
                total_amount=float(tx.get("totalAmount") or tx.get("total_amount") or 0),
                previous_stock=int(tx.get("previousStock") or tx.get("previous_stock") or 0),
                new_stock=int(tx.get("newStock") or tx.get("new_stock") or 0),
                return_condition=tx.get("returnCondition") or tx.get("return_condition") or "N/A",
                status="RECOVERED",
                created_at=__import__("datetime").datetime.utcnow()
            )
            db.add(new_tx)
            upserted += 1

            # Also update product stock in DB to match recovered state
            new_stk = int(tx.get("newStock") or tx.get("new_stock") or -1)
            prod_id = tx.get("productId") or tx.get("product_id")
            if new_stk >= 0 and prod_id:
                product = db.query(models.Product).filter(
                    models.Product.id == prod_id,
                    models.Product.user_id == user_id
                ).first()
                if not product:
                    product = db.query(models.Product).filter(
                        models.Product.user_id == user_id,
                        models.Product.name == (tx.get("productName") or tx.get("product_name", ""))
                    ).first()
                if product:
                    product.stock = new_stk
                    db.add(product)

    db.commit()
    return {"synced": upserted, "updated": skipped, "total": len(transactions)}


@router.get("/report", response_model=Optional[schemas.RecoveryReport])
def get_latest_recovery_report(
    user_id: CurrentUser,
    db: Session = Depends(get_db)
):
    """
    Returns the most recent recovery report generated for this user.
    """
    status_resp = resilience_manager.get_system_status(user_id=user_id, db=db)
    return status_resp.latest_report


@router.get("/live-data")
def get_live_inspector_data(
    user_id: Optional[str] = None,
    db: Session = Depends(get_db)
):
    """
    Public live telemetry endpoint for the real-time presentation dashboard.
    Aggregates across all active store vendors or filters by specific vendor.
    """
    distinct_users = [u[0] for u in db.query(models.Product.user_id).distinct().all() if u[0]]
    if not distinct_users:
        distinct_users = ["harsh", "himanshu", "vishwajeet"]

    is_all = (not user_id or user_id == "all")

    if is_all:
        all_status = [resilience_manager.get_system_status(user_id=u, db=db) for u in distinct_users]
        is_blackout = any(s.is_blackout_active for s in all_status)
        for u in distinct_users:
            if resilience_manager.is_blackout_active(user_id=u):
                is_blackout = True

        system_status = "BLACKOUT_ACTIVE" if is_blackout else ("RECOVERED" if any(s.system_status == "RECOVERED" for s in all_status) else "HEALTHY")
        total_journaled = sum(s.total_journaled_transactions for s in all_status)
        pending_count = sum(s.pending_recovery_count for s in all_status)
        recovered_count = sum(s.recovered_transactions_count for s in all_status)
        last_checkpoint_id = all_status[0].last_checkpoint_id if all_status else None
        last_verified = all_status[0].last_verified_checkpoint if all_status else None
        
        # Prioritize the active mobile device user
        mobile_users = [u for u in distinct_users if len(u) > 15]
        active_user = mobile_users[0] if mobile_users else distinct_users[0]
        
        products_db = db.query(models.Product).filter(models.Product.user_id == active_user).all()
        if not products_db:
            products_db = db.query(models.Product).order_by(models.Product.name).limit(50).all()

        journal_txns = db.query(models.JournalTransaction).order_by(desc(models.JournalTransaction.created_at)).limit(30).all()
        selected_user = "all"

    else:
        selected_user = user_id
        status_resp = resilience_manager.get_system_status(user_id=selected_user, db=db)
        is_blackout = status_resp.is_blackout_active
        system_status = status_resp.system_status
        total_journaled = status_resp.total_journaled_transactions
        pending_count = status_resp.pending_recovery_count
        recovered_count = status_resp.recovered_transactions_count
        last_checkpoint_id = status_resp.last_checkpoint_id
        last_verified = status_resp.last_verified_checkpoint
        
        products_db = db.query(models.Product).filter(models.Product.user_id == selected_user).all()
        if not products_db:
            products_db = db.query(models.Product).limit(50).all()

        journal_txns = db.query(models.JournalTransaction).filter(
            models.JournalTransaction.user_id == selected_user
        ).order_by(desc(models.JournalTransaction.created_at)).limit(30).all()

    return {
        "selected_user": selected_user,
        "available_users": distinct_users,
        "system_status": system_status,
        "is_blackout_active": is_blackout,
        "last_checkpoint_id": last_checkpoint_id,
        "last_verified_checkpoint": last_verified,
        "pending_recovery_count": pending_count,
        "recovered_count": recovered_count,
        "total_journaled": total_journaled,
        "products": [
            {
                "id": p.id,
                "name": p.name,
                "category": p.category or "General",
                "stock": p.stock,
                "unit": p.unit or "pcs",
                "price": p.price
            }
            for p in products_db
        ],
        "journal": [
            {
                "id": t.id,
                "transaction_id": t.transaction_id,
                "type": t.type,
                "product_name": t.product_name,
                "quantity": t.quantity,
                "previous_stock": t.previous_stock,
                "new_stock": t.new_stock,
                "status": t.status,
                "created_at": str(t.created_at)
            }
            for t in journal_txns
        ]
    }



@router.post("/live-action/simulate-blackout")
def live_simulate_blackout(
    user_id: Optional[str] = "harsh",
    db: Session = Depends(get_db)
):
    return resilience_manager.simulate_blackout(user_id=user_id or "harsh", db=db)


@router.post("/live-action/restore")
def live_restore(
    user_id: Optional[str] = "harsh",
    db: Session = Depends(get_db)
):
    return resilience_manager.restore_system(user_id=user_id or "harsh", db=db)


@router.post("/live-action/reset")
def live_reset(
    user_id: Optional[str] = "harsh",
    db: Session = Depends(get_db)
):
    return resilience_manager.reset_demo(user_id=user_id or "harsh", db=db)


@router.post("/live-action/sale")
def live_quick_sale(
    user_id: Optional[str] = "harsh",
    product_name: Optional[str] = None,
    quantity: int = 5,
    db: Session = Depends(get_db)
):
    """
    Simulates a live sale during blackout / normal mode to see the live DB change immediately.
    """
    import uuid
    from datetime import datetime
    selected_user = user_id or "harsh"
    
    product = None
    if product_name:
        product = db.query(models.Product).filter(
            models.Product.user_id == selected_user,
            models.Product.name.ilike(f"%{product_name}%")
        ).first()
    if not product:
        product = db.query(models.Product).filter(models.Product.user_id == selected_user).first()
    
    if not product:
        raise HTTPException(status_code=404, detail="No product found to sell")

    prev_stock = product.stock
    is_blackout = resilience_manager.is_blackout_active(user_id=selected_user)
    
    # If not blackout, deduct DB directly; if blackout, journal it!
    new_stock = max(0, prev_stock - quantity)
    if not is_blackout:
        product.stock = new_stock
        db.commit()

    txn_id = f"TXN_{int(datetime.utcnow().timestamp() * 1000)}_{uuid.uuid4().hex[:6]}"
    resilience_manager.record_journal_entry(
        user_id=selected_user,
        tx_type="SALE",
        product_id=product.id,
        product_name=product.name,
        quantity=quantity,
        unit_price=product.price,
        total_amount=product.price * quantity,
        previous_stock=prev_stock,
        new_stock=new_stock,
        status="PENDING" if is_blackout else "APPLIED",
        transaction_id=txn_id,
        db=db
    )


    return {
        "success": True,
        "product": product.name,
        "previous_stock": prev_stock,
        "new_stock": new_stock,
        "is_blackout": is_blackout,
        "transaction_id": txn_id
    }


from fastapi.responses import HTMLResponse

@router.get("/live-inspector", response_class=HTMLResponse)
def get_live_inspector_page():
    """
    Interactive Real-Time Database Inspector UI for Live Hackathon Presentations & Judges.
    """
    html_content = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SmartVendor — Live Database & Resilience Inspector</title>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;600;700&display=swap" rel="stylesheet">
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: 'Inter', sans-serif;
            background: #090D16;
            color: #E2E8F0;
            padding: 24px;
            min-height: 100vh;
        }
        .header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding-bottom: 20px;
            border-bottom: 1px solid #1E293B;
            margin-bottom: 20px;
            flex-wrap: wrap;
            gap: 16px;
        }
        .title {
            font-size: 24px;
            font-weight: 800;
            background: linear-gradient(135deg, #38BDF8, #818CF8);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
        .subtitle { font-size: 13px; color: #94A3B8; margin-top: 4px; }
        .controls-bar {
            display: flex;
            gap: 10px;
            align-items: center;
            flex-wrap: wrap;
            margin-bottom: 20px;
            background: #131B2E;
            padding: 12px 18px;
            border-radius: 12px;
            border: 1px solid #1E293B;
        }
        .btn {
            padding: 8px 16px;
            border-radius: 8px;
            font-weight: 700;
            font-size: 13px;
            cursor: pointer;
            border: none;
            display: inline-flex;
            align-items: center;
            gap: 6px;
            transition: all 0.2s ease;
        }
        .btn:hover { transform: translateY(-1px); filter: brightness(1.1); }
        .btn:active { transform: translateY(0); }
        .btn-red { background: #EF4444; color: white; }
        .btn-green { background: #10B981; color: white; }
        .btn-blue { background: #3B82F6; color: white; }
        .btn-gray { background: #334155; color: #E2E8F0; }
        
        select {
            background: #0F172A;
            color: #F8FAFC;
            border: 1px solid #334155;
            padding: 8px 14px;
            border-radius: 8px;
            font-size: 13px;
            font-weight: 600;
            cursor: pointer;
        }

        .badge {
            padding: 6px 14px;
            border-radius: 20px;
            font-size: 12px;
            font-weight: 700;
            letter-spacing: 0.5px;
            display: inline-flex;
            align-items: center;
            gap: 6px;
        }
        .badge-healthy { background: rgba(16, 185, 129, 0.15); color: #10B981; border: 1px solid rgba(16, 185, 129, 0.3); }
        .badge-blackout { background: rgba(239, 68, 68, 0.2); color: #EF4444; border: 1px solid #EF4444; animation: pulse 2s infinite; }
        .badge-recovered { background: rgba(59, 130, 246, 0.15); color: #3B82F6; border: 1px solid rgba(59, 130, 246, 0.3); }
        @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.6; } }

        .metrics-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 16px;
            margin-bottom: 24px;
        }
        .card {
            background: #131B2E;
            border: 1px solid #1E293B;
            border-radius: 14px;
            padding: 18px;
        }
        .card-label { font-size: 11px; font-weight: 600; color: #94A3B8; text-transform: uppercase; letter-spacing: 0.5px; }
        .card-value { font-size: 22px; font-weight: 800; margin-top: 6px; font-family: 'JetBrains Mono', monospace; }
        
        .main-grid {
            display: grid;
            grid-template-columns: 1.2fr 1fr;
            gap: 24px;
        }
        @media (max-width: 950px) { .main-grid { grid-template-columns: 1fr; } }
        
        .section-title {
            font-size: 15px;
            font-weight: 700;
            margin-bottom: 12px;
            display: flex;
            align-items: center;
            gap: 8px;
            color: #F1F5F9;
        }
        
        table {
            width: 100%;
            border-collapse: collapse;
            font-size: 13px;
        }
        th {
            text-align: left;
            padding: 10px 12px;
            background: #0F172A;
            color: #94A3B8;
            font-weight: 600;
            font-size: 11px;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            border-bottom: 1px solid #1E293B;
        }
        td {
            padding: 12px;
            border-bottom: 1px solid #1E293B;
        }
        tr:hover { background: rgba(30, 41, 59, 0.4); }
        .stock-val { font-family: 'JetBrains Mono', monospace; font-weight: 800; font-size: 14px; color: #10B981; }
        .tag-sale { background: rgba(59, 130, 246, 0.2); color: #60A5FA; padding: 2px 8px; border-radius: 6px; font-weight: 700; font-size: 11px; }
        .tag-return { background: rgba(239, 68, 68, 0.2); color: #F87171; padding: 2px 8px; border-radius: 6px; font-weight: 700; font-size: 11px; }
        .status-pill { padding: 3px 8px; border-radius: 6px; font-size: 10px; font-weight: 700; }
        .status-pending { background: rgba(245, 158, 11, 0.2); color: #F59E0B; }
        .status-recovered { background: rgba(16, 185, 129, 0.2); color: #10B981; }
        .status-applied { background: rgba(59, 130, 246, 0.2); color: #3B82F6; }

        .live-dot {
            width: 8px;
            height: 8px;
            background: #10B981;
            border-radius: 50%;
            display: inline-block;
            box-shadow: 0 0 8px #10B981;
            animation: pulse 1.5s infinite;
        }
        .toast {
            position: fixed;
            bottom: 24px;
            right: 24px;
            background: #1E293B;
            border: 1px solid #38BDF8;
            color: #F8FAFC;
            padding: 12px 20px;
            border-radius: 10px;
            box-shadow: 0 10px 25px rgba(0,0,0,0.5);
            font-size: 13px;
            font-weight: 600;
            display: none;
            z-index: 100;
        }
    </style>
</head>
<body>
    <div id="toast" class="toast">Action completed!</div>

    <div class="header">
        <div>
            <div class="title">⚡ SmartVendor Live Database Telemetry</div>
            <div class="subtitle">Real-Time Write-Ahead Journal & Primary Database Synchronization Monitor</div>
        </div>
        <div style="display: flex; align-items: center; gap: 14px;">
            <div style="display: flex; align-items: center; gap: 6px; font-size: 12px; color: #94A3B8;">
                <span class="live-dot"></span> Polling (1s)
            </div>
            <div id="statusBadge" class="badge badge-healthy">🟢 HEALTHY</div>
        </div>
    </div>

    <!-- Interactive Hackathon Controls -->
    <div class="controls-bar">
        <span style="font-size: 12px; font-weight: 700; color: #94A3B8; text-transform: uppercase;">Monitor View:</span>
        <select id="userSelect" onchange="fetchTelemetry()">
            <option value="uXXp4u9hvxP9hrv22LvllrlX6hx1" selected>📱 My Connected Phone (Active Store)</option>
            <option value="all">🌍 All Stores (Global Overview)</option>
            <option value="harsh">Harsh (General Store Demo)</option>
            <option value="himanshu">Himanshu (Kirana)</option>
            <option value="vishwajeet">Vishwajeet (Supermart)</option>
        </select>

        <div style="height: 20px; width: 1px; background: #334155; margin: 0 8px;"></div>

        <button class="btn btn-red" onclick="triggerAction('simulate-blackout')">🔴 Simulate Blackout</button>
        <button class="btn btn-blue" onclick="triggerAction('sale')">🛒 Test Sale (-5)</button>
        <button class="btn btn-green" onclick="triggerAction('restore')">🔄 Restore System</button>
        <button class="btn btn-gray" onclick="triggerAction('reset')">♻ Reset Demo</button>
    </div>



    <div class="metrics-grid">
        <div class="card">
            <div class="card-label">Primary Database State</div>
            <div id="dbState" class="card-value" style="color: #10B981;">ONLINE</div>
        </div>
        <div class="card">
            <div class="card-label">Total Journaled Txns</div>
            <div id="totalJournaled" class="card-value" style="color: #38BDF8;">0</div>
        </div>
        <div class="card">
            <div class="card-label">Pending Recovery</div>
            <div id="pendingCount" class="card-value" style="color: #F59E0B;">0</div>
        </div>
        <div class="card">
            <div class="card-label">Recovered Txns</div>
            <div id="recoveredCount" class="card-value" style="color: #10B981;">0</div>
        </div>
    </div>

    <div class="main-grid">
        <!-- Live Primary Database Inventory Table -->
        <div class="card">
            <div class="section-title">
                <span>🗄️</span> Primary Database Inventory (Live Server State)
            </div>
            <table>
                <thead>
                    <tr>
                        <th>Product</th>
                        <th>Category</th>
                        <th>DB Stock</th>
                        <th>Action</th>
                    </tr>
                </thead>
                <tbody id="productsTableBody">
                    <tr><td colspan="4" style="text-align: center; color: #64748B;">Connecting to database...</td></tr>
                </tbody>
            </table>
        </div>

        <!-- Write-Ahead Transaction Journal Feed -->
        <div class="card">
            <div class="section-title">
                <span>📜</span> Live Write-Ahead Journal Feed
            </div>
            <table>
                <thead>
                    <tr>
                        <th>Type</th>
                        <th>Item</th>
                        <th>Qty</th>
                        <th>Transition</th>
                        <th>Status</th>
                    </tr>
                </thead>
                <tbody id="journalTableBody">
                    <tr><td colspan="5" style="text-align: center; color: #64748B;">No journal entries recorded yet.</td></tr>
                </tbody>
            </table>
        </div>
    </div>

    <script>
        function showToast(msg) {
            const t = document.getElementById('toast');
            t.innerText = msg;
            t.style.display = 'block';
            setTimeout(() => { t.style.display = 'none'; }, 3000);
        }

        async function triggerAction(action) {
            const user = document.getElementById('userSelect').value || 'harsh';
            try {
                const res = await fetch(`/resilience/live-action/${action}?user_id=${user}`, { method: 'POST' });
                const result = await res.json();
                if (action === 'simulate-blackout') showToast('🔴 Blackout Mode Activated!');
                if (action === 'sale') showToast(`🛒 Sold 5x ${result.product} (${result.previous_stock} ➔ ${result.new_stock})`);
                if (action === 'restore') showToast(`🟢 System Restored! Replayed ${result.successfully_recovered} txns.`);
                if (action === 'reset') showToast('♻ Demo Reset to clean baseline.');
                fetchTelemetry();
            } catch (e) {
                console.error(e);
            }
        }

        async function fetchTelemetry() {
            try {
                const selectEl = document.getElementById('userSelect');
                const user = selectEl.value || 'all';
                const res = await fetch(`/resilience/live-data?user_id=${user}`);
                if (!res.ok) return;
                const data = await res.json();

                // Dynamically append any new unknown users (but never move away from phone user as default)
                const PHONE_UID = 'uXXp4u9hvxP9hrv22LvllrlX6hx1';
                const knownUsers = ['uXXp4u9hvxP9hrv22LvllrlX6hx1', 'all', 'harsh', 'himanshu', 'vishwajeet'];
                if (data.available_users && data.available_users.length > 0) {
                    const existingValues = Array.from(selectEl.options).map(o => o.value);
                    data.available_users.forEach(u => {
                        if (!existingValues.includes(u) && !knownUsers.includes(u)) {
                            const opt = document.createElement('option');
                            opt.value = u;
                            opt.innerText = '📱 Mobile Vendor (' + u.substring(0, 8) + '...)';
                            selectEl.appendChild(opt);
                        }
                    });
                }
                // Always ensure phone user is selected if it hasn't been changed by user
                if (!selectEl.dataset.userChanged && selectEl.value !== PHONE_UID) {
                    selectEl.value = PHONE_UID;
                }


                // Update Status Badge
                const badge = document.getElementById('statusBadge');
                const dbState = document.getElementById('dbState');
                if (data.is_blackout_active) {
                    badge.className = 'badge badge-blackout';
                    badge.innerHTML = '🔴 BLACKOUT ACTIVE';
                    dbState.innerText = 'CORRUPTED / OFFLINE';
                    dbState.style.color = '#EF4444';
                } else if (data.system_status === 'RECOVERED') {
                    badge.className = 'badge badge-recovered';
                    badge.innerHTML = '🟢 RECOVERED ✓';
                    dbState.innerText = 'ONLINE / SYNCHRONIZED';
                    dbState.style.color = '#10B981';
                } else {
                    badge.className = 'badge badge-healthy';
                    badge.innerHTML = '🟢 SYSTEM HEALTHY';
                    dbState.innerText = 'ONLINE';
                    dbState.style.color = '#10B981';
                }

                // Update Counters
                document.getElementById('totalJournaled').innerText = data.total_journaled;
                document.getElementById('pendingCount').innerText = data.pending_recovery_count;
                document.getElementById('recoveredCount').innerText = data.recovered_count;

                // Update Products Table
                const pBody = document.getElementById('productsTableBody');
                if (data.products && data.products.length > 0) {
                    pBody.innerHTML = data.products.slice(0, 10).map(p => `
                        <tr>
                            <td><strong>${p.name}</strong></td>
                            <td style="color: #94A3B8;">${p.category || 'General'}</td>
                            <td><span class="stock-val">${p.stock}</span> <span style="font-size: 11px; color: #64748B;">${p.unit || 'pcs'}</span></td>
                            <td>
                                <button onclick="triggerQuickItemSale('${p.name}')" style="background: rgba(59, 130, 246, 0.15); color: #60A5FA; border: 1px solid rgba(59, 130, 246, 0.3); border-radius: 6px; padding: 3px 8px; font-size: 11px; cursor: pointer; font-weight: 700;">-5 Qty</button>
                            </td>
                        </tr>
                    `).join('');
                }

                // Update Journal Table
                const jBody = document.getElementById('journalTableBody');
                if (data.journal && data.journal.length > 0) {
                    jBody.innerHTML = data.journal.map(j => {
                        const isReturn = (j.type || '').toUpperCase() === 'RETURN';
                        const typeTag = isReturn ? '<span class="tag-return">RETURN</span>' : '<span class="tag-sale">SALE</span>';
                        let statusClass = 'status-applied';
                        let statusText = j.status;
                        if (j.status === 'PENDING') { statusClass = 'status-pending'; statusText = 'PENDING'; }
                        if (j.status === 'RECOVERED') { statusClass = 'status-recovered'; statusText = 'RECOVERED ✓'; }
                        
                        const transition = (j.previous_stock !== null && j.new_stock !== null) 
                            ? `<span style="font-family: 'JetBrains Mono'; font-size: 11px; color: #E2E8F0;">${j.previous_stock} ➔ ${j.new_stock}</span>`
                            : '-';

                        return `
                            <tr>
                                <td>${typeTag}</td>
                                <td><strong>${j.product_name || 'Item'}</strong></td>
                                <td style="font-family: 'JetBrains Mono'; font-weight: 700;">${j.quantity}</td>
                                <td>${transition}</td>
                                <td><span class="status-pill ${statusClass}">${statusText}</span></td>
                            </tr>
                        `;
                    }).join('');
                } else {
                    jBody.innerHTML = '<tr><td colspan="5" style="text-align: center; color: #64748B;">No journal entries recorded yet.</td></tr>';
                }
            } catch (err) {
                console.error("Telemetry fetch failed:", err);
            }
        }

        async function triggerQuickItemSale(name) {
            const user = document.getElementById('userSelect').value || 'harsh';
            try {
                const res = await fetch(`/resilience/live-action/sale?user_id=${user}&product_name=${encodeURIComponent(name)}&quantity=5`, { method: 'POST' });
                const result = await res.json();
                showToast(`🛒 Sold 5x ${result.product} (${result.previous_stock} ➔ ${result.new_stock})`);
                fetchTelemetry();
            } catch(e) {
                console.error(e);
            }
        }

        // Force phone user on initial load
        const sel = document.getElementById('userSelect');
        sel.value = 'uXXp4u9hvxP9hrv22LvllrlX6hx1';
        sel.addEventListener('change', () => { sel.dataset.userChanged = '1'; });

        // Poll every 1000ms
        setInterval(fetchTelemetry, 1000);
        fetchTelemetry();
    </script>
</body>
</html>
"""
    return HTMLResponse(content=html_content)


