import os
import json
import uuid
import time
from datetime import datetime
from typing import Dict, List, Optional, Any, Tuple
from sqlalchemy.orm import Session
from sqlalchemy import func, desc, text

import models
import schemas

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JOURNAL_DIR = os.path.join(BASE_DIR, "journal_storage")
CHECKPOINT_DIR = os.path.join(BASE_DIR, "checkpoint_storage")

os.makedirs(JOURNAL_DIR, exist_ok=True)
os.makedirs(CHECKPOINT_DIR, exist_ok=True)


class ResilienceManager:
    """
    Centralized Resilience & Recovery Manager:
    - Append-oriented durable transaction journal (DB + append-only JSONL files)
    - Checkpoint snapshotting (Products, Stock, Bills, Watermark)
    - Controlled Blackout Simulation (in-memory & flag-based)
    - Idempotent Transaction Replay Engine
    - Detailed Recovery Reporting
    """

    def __init__(self):
        # In-memory blackout state by user_id
        self._blackout_state: Dict[str, Dict[str, Any]] = {}
        # Latest recovery reports cache
        self._latest_reports: Dict[str, schemas.RecoveryReport] = {}

    def is_blackout_active(self, user_id: str) -> bool:
        state = self._blackout_state.get(user_id)
        return bool(state and state.get("is_active", False))

    def get_blackout_info(self, user_id: str) -> Dict[str, Any]:
        return self._blackout_state.get(user_id, {
            "is_active": False,
            "started_at": None,
            "reason": None
        })

    def get_system_status(self, user_id: str, db: Session) -> schemas.SystemStatusResponse:
        blackout_info = self.get_blackout_info(user_id)
        is_active = blackout_info.get("is_active", False)

        # Query latest checkpoint
        latest_checkpoint = db.query(models.SystemCheckpoint).filter(
            models.SystemCheckpoint.user_id == user_id
        ).order_by(desc(models.SystemCheckpoint.created_at)).first()

        # Query journal counts
        total_tx = db.query(func.count(models.JournalTransaction.id)).filter(
            models.JournalTransaction.user_id == user_id
        ).scalar() or 0

        pending_tx = db.query(func.count(models.JournalTransaction.id)).filter(
            models.JournalTransaction.user_id == user_id,
            models.JournalTransaction.status == "PENDING"
        ).scalar() or 0

        recovered_tx = db.query(func.count(models.JournalTransaction.id)).filter(
            models.JournalTransaction.user_id == user_id,
            models.JournalTransaction.status == "RECOVERED"
        ).scalar() or 0

        system_status = "BLACKOUT_ACTIVE" if is_active else "HEALTHY"
        db_status = "CORRUPTED_UNAVAILABLE" if is_active else "ONLINE"

        return schemas.SystemStatusResponse(
            system_status=system_status,
            is_blackout_active=is_active,
            blackout_started_at=blackout_info.get("started_at"),
            simulated_failure_reason=blackout_info.get("reason"),
            primary_database_status=db_status,
            last_verified_checkpoint=latest_checkpoint.created_at if latest_checkpoint else None,
            last_checkpoint_id=latest_checkpoint.checkpoint_id if latest_checkpoint else None,
            total_journaled_transactions=total_tx,
            pending_recovery_count=pending_tx,
            recovered_transactions_count=recovered_tx,
            latest_report=self._latest_reports.get(user_id)
        )

    # -------------------------------------------------------------------------
    # 1. Checkpointing
    # -------------------------------------------------------------------------

    def create_checkpoint(
        self,
        user_id: str,
        db: Session,
        checkpoint_type: str = "AUTO"
    ) -> models.SystemCheckpoint:
        """
        Creates an atomic, durable snapshot of products, inventory, bills, and transaction watermark.
        """
        now = datetime.utcnow()
        checkpoint_id = f"CHK_{int(time.time() * 1000)}_{uuid.uuid4().hex[:6]}"

        # Snapshot products
        products = db.query(models.Product).filter(models.Product.user_id == user_id).all()
        products_data = [
            {
                "id": p.id,
                "name": p.name,
                "barcode": p.barcode,
                "category": p.category,
                "price": float(p.price),
                "stock": int(p.stock),
                "low_stock_threshold": int(p.low_stock_threshold),
                "unit": p.unit,
                "seasonal_profile": p.seasonal_profile
            }
            for p in products
        ]

        # Snapshot bills
        bills = db.query(models.Bill).filter(models.Bill.user_id == user_id).all()
        bills_data = [
            {
                "id": b.id,
                "transaction_type": b.transaction_type,
                "total_amount": float(b.total_amount),
                "tax_amount": float(b.tax_amount),
                "payment_mode": b.payment_mode,
                "created_at": b.created_at.isoformat() if b.created_at else None,
                "items": [
                    {
                        "id": item.id,
                        "product_id": item.product_id,
                        "product_name": item.product_name,
                        "quantity": item.quantity,
                        "unit_price": float(item.unit_price),
                        "total_price": float(item.total_price),
                        "condition": item.condition
                    }
                    for item in b.items
                ]
            }
            for b in bills
        ]

        # Find latest transaction ID watermark
        latest_tx = db.query(models.JournalTransaction).filter(
            models.JournalTransaction.user_id == user_id
        ).order_by(desc(models.JournalTransaction.created_at)).first()
        last_tx_id = latest_tx.transaction_id if latest_tx else None

        snapshot_dict = {
            "checkpoint_id": checkpoint_id,
            "user_id": user_id,
            "timestamp": now.isoformat(),
            "products_count": len(products_data),
            "bills_count": len(bills_data),
            "last_transaction_id": last_tx_id,
            "products": products_data,
            "bills": bills_data
        }
        snapshot_json = json.dumps(snapshot_dict)

        # Save to database
        checkpoint = models.SystemCheckpoint(
            id=str(uuid.uuid4()),
            user_id=user_id,
            checkpoint_id=checkpoint_id,
            checkpoint_type=checkpoint_type,
            snapshot_data=snapshot_json,
            products_count=len(products_data),
            bills_count=len(bills_data),
            last_transaction_id=last_tx_id,
            created_at=now
        )
        db.add(checkpoint)
        db.commit()
        db.refresh(checkpoint)

        # Save backup file to disk
        try:
            file_path = os.path.join(CHECKPOINT_DIR, f"{checkpoint_id}.json")
            with open(file_path, "w", encoding="utf-8") as f:
                f.write(snapshot_json)
        except Exception as e:
            print(f"Warning writing disk checkpoint: {e}")

        return checkpoint

    # -------------------------------------------------------------------------
    # 2. Durable Transaction Journaling
    # -------------------------------------------------------------------------

    def record_journal_entry(
        self,
        user_id: str,
        db: Session,
        tx_type: str,  # SALE, RETURN, STOCK_ADJUST
        bill_id: Optional[str] = None,
        product_id: Optional[str] = None,
        product_name: Optional[str] = None,
        quantity: int = 0,
        unit_price: float = 0.0,
        total_amount: float = 0.0,
        previous_stock: Optional[int] = None,
        new_stock: Optional[int] = None,
        return_condition: str = "GOOD",
        status: str = "APPLIED",
        payload_dict: Optional[Dict[str, Any]] = None,
        transaction_id: Optional[str] = None,
        timestamp: Optional[datetime] = None
    ) -> models.JournalTransaction:
        """
        Appends a critical data mutation to the durable transaction journal.
        """
        now = timestamp or datetime.utcnow()
        if not transaction_id:
            transaction_id = f"TXN_{int(time.time() * 1000)}_{uuid.uuid4().hex[:6]}"

        payload_json = json.dumps(payload_dict, default=str) if payload_dict else None

        # Check if transaction_id already exists (idempotency)
        existing = db.query(models.JournalTransaction).filter(
            models.JournalTransaction.transaction_id == transaction_id
        ).first()
        if existing:
            return existing

        journal_entry = models.JournalTransaction(
            id=str(uuid.uuid4()),
            transaction_id=transaction_id,
            user_id=user_id,
            type=tx_type,
            bill_id=bill_id,
            product_id=product_id,
            product_name=product_name,
            quantity=quantity,
            unit_price=unit_price,
            total_amount=total_amount,
            previous_stock=previous_stock,
            new_stock=new_stock,
            return_condition=return_condition,
            status=status,
            payload_json=payload_json,
            timestamp=now,
            created_at=now
        )
        db.add(journal_entry)
        db.commit()
        db.refresh(journal_entry)

        # Append to durable disk journal (JSON Lines)
        try:
            journal_file = os.path.join(JOURNAL_DIR, f"journal_{user_id}.jsonl")
            entry_dict = {
                "transaction_id": transaction_id,
                "user_id": user_id,
                "type": tx_type,
                "bill_id": bill_id,
                "product_id": product_id,
                "product_name": product_name,
                "quantity": quantity,
                "unit_price": unit_price,
                "total_amount": total_amount,
                "previous_stock": previous_stock,
                "new_stock": new_stock,
                "return_condition": return_condition,
                "status": status,
                "payload_json": payload_dict,
                "timestamp": now.isoformat()
            }
            with open(journal_file, "a", encoding="utf-8") as f:
                f.write(json.dumps(entry_dict) + "\n")
        except Exception as e:
            print(f"Warning appending to disk journal: {e}")

        return journal_entry

    # -------------------------------------------------------------------------
    # 3. Controlled Blackout Simulation
    # -------------------------------------------------------------------------

    def simulate_blackout(
        self,
        user_id: str,
        db: Session,
        reason: str = "Primary Database Unavailable / Corrupted"
    ) -> schemas.SystemStatusResponse:
        """
        Activates Blackout Mode:
        1. Ensures a valid LAST_KNOWN_GOOD_CHECKPOINT exists before the blackout begins.
        2. Activates blackout flag.
        3. All subsequent operations continue via durable journaling.
        """
        # Ensure a checkpoint exists
        latest_checkpoint = db.query(models.SystemCheckpoint).filter(
            models.SystemCheckpoint.user_id == user_id
        ).order_by(desc(models.SystemCheckpoint.created_at)).first()

        if not latest_checkpoint:
            self.create_checkpoint(user_id, db, checkpoint_type="PRE_BLACKOUT")

        now = datetime.utcnow()
        self._blackout_state[user_id] = {
            "is_active": True,
            "started_at": now,
            "reason": reason
        }

        return self.get_system_status(user_id, db)

    # -------------------------------------------------------------------------
    # 4. Idempotent Recovery Engine
    # -------------------------------------------------------------------------

    def restore_system(self, user_id: str, db: Session) -> schemas.RecoveryReport:
        """
        RESTORE SYSTEM:
        Step 1: Load the last known good checkpoint.
        Step 2: Read all journaled transactions after that checkpoint (or with status PENDING/recorded during blackout).
        Step 3: Check each transaction ID for idempotency.
        Step 4: Skip transactions that already exist / are applied.
        Step 5: Replay missing transactions in chronological order:
                - SALE: Deducts stock (stock = max(0, stock - qty))
                - RETURN + GOOD: Adds stock (stock = stock + qty)
                - RETURN + DAMAGED: Stock unchanged (stock = stock + 0)
                - Re-creates missing bill and bill items.
        Step 6: Mark successfully replayed transactions as RECOVERED.
        Step 7: Deactivate blackout mode.
        Step 8: Generate and return structured Recovery Report.
        """
        now = datetime.utcnow()

        # Step 1: Load latest checkpoint
        latest_checkpoint = db.query(models.SystemCheckpoint).filter(
            models.SystemCheckpoint.user_id == user_id
        ).order_by(desc(models.SystemCheckpoint.created_at)).first()

        if not latest_checkpoint:
            # Fallback: create an initial baseline checkpoint if none exists
            latest_checkpoint = self.create_checkpoint(user_id, db, checkpoint_type="MANUAL")

        checkpoint_data = json.loads(latest_checkpoint.snapshot_data)
        checkpoint_time = latest_checkpoint.created_at
        checkpoint_products = {p["id"]: p for p in checkpoint_data.get("products", [])}
        checkpoint_bills = {b["id"]: b for b in checkpoint_data.get("bills", [])}

        # Step 2: Restore products to checkpoint baseline stock
        for prod_id, p_info in checkpoint_products.items():
            product = db.query(models.Product).filter(
                models.Product.id == prod_id,
                models.Product.user_id == user_id
            ).first()
            if product:
                product.stock = p_info["stock"]
            else:
                # Re-create product if missing
                new_prod = models.Product(
                    id=prod_id,
                    user_id=user_id,
                    name=p_info["name"],
                    barcode=p_info.get("barcode"),
                    category=p_info.get("category", "General"),
                    price=p_info["price"],
                    stock=p_info["stock"],
                    low_stock_threshold=p_info.get("low_stock_threshold", 5),
                    unit=p_info.get("unit", "pcs"),
                    seasonal_profile=p_info.get("seasonal_profile", "STABLE")
                )
                db.add(new_prod)
        db.commit()

        # Step 3: Discover all journal transactions for this user
        # 3A. From database
        db_transactions = db.query(models.JournalTransaction).filter(
            models.JournalTransaction.user_id == user_id
        ).all()
        journal_map: Dict[str, models.JournalTransaction] = {t.transaction_id: t for t in db_transactions}

        # 3B. From disk journal file (for resilience if DB was cleared)
        disk_file = os.path.join(JOURNAL_DIR, f"journal_{user_id}.jsonl")
        if os.path.exists(disk_file):
            try:
                with open(disk_file, "r", encoding="utf-8") as f:
                    for line in f:
                        line = line.strip()
                        if line:
                            entry = json.loads(line)
                            tx_id = entry.get("transaction_id")
                            if tx_id and tx_id not in journal_map:
                                # Re-inject missing journal entry into DB
                                ts = datetime.fromisoformat(entry["timestamp"]) if entry.get("timestamp") else datetime.utcnow()
                                new_entry = models.JournalTransaction(
                                    id=str(uuid.uuid4()),
                                    transaction_id=tx_id,
                                    user_id=user_id,
                                    type=entry.get("type", "SALE"),
                                    bill_id=entry.get("bill_id"),
                                    product_id=entry.get("product_id"),
                                    product_name=entry.get("product_name"),
                                    quantity=entry.get("quantity", 0),
                                    unit_price=entry.get("unit_price", 0.0),
                                    total_amount=entry.get("total_amount", 0.0),
                                    previous_stock=entry.get("previous_stock"),
                                    new_stock=entry.get("new_stock"),
                                    return_condition=entry.get("return_condition", "GOOD"),
                                    status="PENDING",
                                    payload_json=json.dumps(entry.get("payload_json")) if entry.get("payload_json") else None,
                                    timestamp=ts,
                                    created_at=ts
                                )
                                db.add(new_entry)
                                journal_map[tx_id] = new_entry
                db.commit()
            except Exception as e:
                print(f"Error reading disk journal: {e}")

        # Step 4: Filter transactions to replay (only those created at/after checkpoint or with status PENDING/during blackout)
        all_tx_list = list(journal_map.values())
        all_tx_list.sort(key=lambda x: x.created_at or datetime.utcnow())

        discovered_count = len(all_tx_list)
        recovered_count = 0
        already_present_count = 0
        unrecoverable_count = 0
        unrecoverable_details = []

        # Current existing bills in DB
        existing_bill_ids = {b.id for b in db.query(models.Bill.id).filter(models.Bill.user_id == user_id).all()}

        for tx in all_tx_list:
            # Check if this transaction occurred before checkpoint and is already in checkpoint
            if tx.created_at and checkpoint_time and tx.created_at < checkpoint_time and tx.status != "PENDING":
                already_present_count += 1
                continue

            if not tx.product_id and not tx.product_name:
                unrecoverable_count += 1
                unrecoverable_details.append(f"Transaction {tx.transaction_id} missing item metadata.")
                tx.status = "FAILED"
                continue

            # Replay the item mutation
            try:
                is_return = (tx.type == "RETURN")
                cond = (tx.return_condition or "GOOD").upper()
                qty = int(tx.quantity or 0)

                # Find product by ID or name
                prod = None
                if tx.product_id:
                    prod = db.query(models.Product).filter(
                        models.Product.id == tx.product_id,
                        models.Product.user_id == user_id
                    ).first()
                if not prod and tx.product_name:
                    prod = db.query(models.Product).filter(
                        models.Product.name == tx.product_name,
                        models.Product.user_id == user_id
                    ).first()

                if prod and qty > 0:
                    if is_return:
                        if cond == "GOOD":
                            prod.stock = prod.stock + qty
                        elif cond == "DAMAGED":
                            pass
                    else:
                        prod.stock = max(0, prod.stock - qty)

                # Create Bill and BillItem if missing
                if tx.bill_id and tx.bill_id not in existing_bill_ids:
                    target_bill_id = tx.bill_id
                    bill_total = float(tx.total_amount or 0.0)
                    tax_amt = round(bill_total * 0.05, 2)
                    bill_record = models.Bill(
                        id=target_bill_id,
                        user_id=user_id,
                        transaction_id=tx.transaction_id,
                        transaction_type="RETURN" if is_return else "BILL",
                        total_amount=bill_total,
                        tax_amount=tax_amt,
                        payment_mode="cash",
                        created_at=tx.created_at or datetime.utcnow()
                    )
                    db.add(bill_record)
                    existing_bill_ids.add(target_bill_id)

                    bill_item = models.BillItem(
                        id=str(uuid.uuid4()),
                        bill_id=target_bill_id,
                        product_id=prod.id if prod else tx.product_id,
                        product_name=prod.name if prod else (tx.product_name or "Custom Item"),
                        quantity=qty,
                        unit_price=float(tx.unit_price or 0.0),
                        total_price=bill_total,
                        condition=cond
                    )
                    db.add(bill_item)

                tx.status = "RECOVERED"
                recovered_count += 1

            except Exception as e:
                unrecoverable_count += 1
                unrecoverable_details.append(f"Error replaying {tx.transaction_id}: {str(e)}")
                tx.status = "FAILED"

        db.commit()

        # Step 7: Clear blackout mode
        self._blackout_state[user_id] = {
            "is_active": False,
            "started_at": None,
            "reason": None
        }

        # Step 8: Build inventory summary for verification report
        current_products = db.query(models.Product).filter(models.Product.user_id == user_id).all()
        inventory_summary = [
            {
                "product_id": p.id,
                "product_name": p.name,
                "current_stock": p.stock,
                "price": p.price
            }
            for p in current_products[:10]  # Top 10 products
        ]
        total_bills_count = db.query(func.count(models.Bill.id)).filter(models.Bill.user_id == user_id).scalar() or 0

        # Create structured report
        report = schemas.RecoveryReport(
            system_status="HEALTHY",
            last_checkpoint_id=latest_checkpoint.checkpoint_id,
            last_checkpoint_timestamp=latest_checkpoint.created_at,
            transactions_discovered=discovered_count,
            successfully_recovered=recovered_count,
            already_present=already_present_count,
            unrecoverable=unrecoverable_count,
            unrecoverable_details=unrecoverable_details,
            inventory_summary=inventory_summary,
            bills_count=total_bills_count,
            report_generated_at=now
        )
        self._latest_reports[user_id] = report
        return report

    # -------------------------------------------------------------------------
    # 5. Demo Reset Helper
    # -------------------------------------------------------------------------

    def reset_demo(self, user_id: str, db: Session) -> schemas.SystemStatusResponse:
        """
        Resets demo to clean baseline:
        - Deactivates blackout
        - Sets demo inventory (Rice=50, Atta=30, Milk=20, Sugar=40)
        - Creates a fresh checkpoint
        """
        self._blackout_state[user_id] = {
            "is_active": False,
            "started_at": None,
            "reason": None
        }

        # Clear disk journal for user
        disk_file = os.path.join(JOURNAL_DIR, f"journal_{user_id}.jsonl")
        if os.path.exists(disk_file):
            try:
                os.remove(disk_file)
            except Exception:
                pass

        # Clear journal table in DB for user
        db.query(models.JournalTransaction).filter(models.JournalTransaction.user_id == user_id).delete()

        # Ensure demo products exist with standard demo stock
        demo_items = [
            ("Rice", "Grocery", 60.0, 50),
            ("Atta", "Grocery", 45.0, 30),
            ("Milk", "Dairy", 30.0, 20),
            ("Sugar", "Grocery", 42.0, 40),
            ("Oreo", "Snacks", 10.0, 80),
            ("Soya Sticks", "Snacks", 20.0, 80),
            ("Jimjam", "Snacks", 15.0, 65),
            ("Appy Fizz", "Beverages", 35.0, 75),
        ]

        for name, cat, price, default_stock in demo_items:
            prod = db.query(models.Product).filter(
                models.Product.name == name,
                models.Product.user_id == user_id
            ).first()
            if prod:
                prod.stock = default_stock
                prod.price = price
            else:
                prod = models.Product(
                    id=str(uuid.uuid4()),
                    user_id=user_id,
                    name=name,
                    category=cat,
                    price=price,
                    stock=default_stock
                )
                db.add(prod)
        db.commit()

        # Create fresh checkpoint
        self.create_checkpoint(user_id, db, checkpoint_type="MANUAL")

        return self.get_system_status(user_id, db)



# Singleton Instance
resilience_manager = ResilienceManager()
