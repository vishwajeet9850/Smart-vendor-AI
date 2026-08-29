import json
import uuid
from datetime import datetime, timedelta
from typing import Dict, Any, List, Optional
from sqlalchemy import func, desc
from sqlalchemy.orm import Session
import models

DEMO_INCIDENT_ID = "cross_vendor_return_demo"


class CIEManager:
    """
    Cross-Vendor Incident Engine (CIE):
    - Anomaly detection across multi-vendor return signals.
    - Isolated, 100% reversible demo incident simulator.
    - Zero interference with production/real store data.
    """

    DEMO_VENDORS = [
        "demo_vendor_om_sharma",
        "demo_vendor_gupta_kirana",
        "demo_vendor_radha_stores",
        "demo_vendor_metro_mart",
        "demo_vendor_balaji_traders",
        "demo_vendor_shree_krishna",
        "demo_vendor_laxmi_general",
        "demo_vendor_patel_provision"
    ]

    @classmethod
    def simulate_cross_vendor_incident(
        cls,
        db: Session,
        current_user_id: str,
        target_product_name: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Injects clustered return transactions for a product across 7–8 vendors
        within a 15–30 minute cluster window.
        Tags every record with demoIncidentId = 'cross_vendor_return_demo'.
        Runs CIE anomaly detection (>= 5 vendors within <= 30 min) and creates an alert.
        """
        # 1. Clean up any prior demo incident first to prevent duplicates
        cls.reset_demo_incident(db, current_user_id)

        # 2. Pick a target product
        product_name = target_product_name
        product_price = 20.0
        product_id = None
        user_product = None

        if not product_name:
            user_product = db.query(models.Product).filter(
                models.Product.user_id == current_user_id
            ).first()
            if user_product:
                product_name = user_product.name
                product_price = float(user_product.price or 20.0)
                product_id = user_product.id
            else:
                product_name = "Soya Sticks"
                product_price = 20.0

        now = datetime.utcnow()
        injected_bills = []
        user_demo_bills_data = []

        # 3. Inject 3 distinct return bills for CURRENT USER (for visible, convincing bill history)
        user_return_specs = [
            {"minutes_ago": 6, "qty": 1, "reason": "Defective packaging seal"},
            {"minutes_ago": 14, "qty": 2, "reason": "Customer complaint (taste anomaly)"},
            {"minutes_ago": 22, "qty": 1, "reason": "Batch return (texture complaint)"}
        ]

        for spec_idx, spec in enumerate(user_return_specs):
            tx_time = now - timedelta(minutes=spec["minutes_ago"])
            tx_id = f"CIE_DEMO_{DEMO_INCIDENT_ID}_{current_user_id}_{int(tx_time.timestamp())}_{spec_idx+1}"
            bill_id = f"BILL_CIE_{DEMO_INCIDENT_ID}_USER_{spec_idx+1}"
            return_qty = spec["qty"]
            total_amt = round(product_price * return_qty, 2)

            bill = models.Bill(
                id=bill_id,
                user_id=current_user_id,
                transaction_id=tx_id,
                transaction_type="RETURN",
                total_amount=total_amt,
                tax_amount=round(total_amt * 0.05, 2),
                payment_mode="CASH",
                created_at=tx_time
            )
            db.add(bill)

            bill_item = models.BillItem(
                id=str(uuid.uuid4()),
                bill_id=bill_id,
                product_id=product_id,
                product_name=product_name,
                quantity=return_qty,
                unit_price=product_price,
                total_price=total_amt,
                condition="DAMAGED"
            )
            db.add(bill_item)

            journal_tx = models.JournalTransaction(
                id=str(uuid.uuid4()),
                transaction_id=tx_id,
                user_id=current_user_id,
                type="RETURN",
                bill_id=bill_id,
                product_id=product_id,
                product_name=product_name,
                quantity=return_qty,
                unit_price=product_price,
                total_amount=total_amt,
                previous_stock=None,
                new_stock=None,
                return_condition="DAMAGED",
                status="APPLIED",
                payload_json=json.dumps({
                    "demoIncidentId": DEMO_INCIDENT_ID,
                    "simulated": True,
                    "product_name": product_name,
                    "reason": spec["reason"],
                    "vendor_id": current_user_id
                }),
                timestamp=tx_time,
                created_at=tx_time
            )
            db.add(journal_tx)
            injected_bills.append(bill_id)
            user_demo_bills_data.append({
                "bill_id": bill_id,
                "product_name": product_name,
                "quantity": return_qty,
                "unit_price": product_price,
                "total_amount": total_amt,
                "payment_method": "CASH",
                "timestamp": int(tx_time.timestamp() * 1000),
                "reason": spec["reason"]
            })

        # Inject returns across 5 additional distinct PARTNER VENDORS
        partner_vendors = [
            "demo_vendor_gupta_kirana",
            "demo_vendor_radha_stores",
            "demo_vendor_metro_mart",
            "demo_vendor_balaji_traders",
            "demo_vendor_shree_krishna"
        ]

        for p_idx, partner_id in enumerate(partner_vendors):
            minutes_ago = 24 - (p_idx * 3)
            tx_time = now - timedelta(minutes=minutes_ago)
            tx_id = f"CIE_DEMO_{DEMO_INCIDENT_ID}_{partner_id}_{int(tx_time.timestamp())}"
            bill_id = f"BILL_CIE_{DEMO_INCIDENT_ID}_PARTNER_{p_idx+1}"
            return_qty = 1 if p_idx % 2 == 0 else 2
            total_amt = round(product_price * return_qty, 2)

            bill = models.Bill(
                id=bill_id,
                user_id=partner_id,
                transaction_id=tx_id,
                transaction_type="RETURN",
                total_amount=total_amt,
                tax_amount=round(total_amt * 0.05, 2),
                payment_mode="CASH",
                created_at=tx_time
            )
            db.add(bill)

            bill_item = models.BillItem(
                id=str(uuid.uuid4()),
                bill_id=bill_id,
                product_id=None,
                product_name=product_name,
                quantity=return_qty,
                unit_price=product_price,
                total_price=total_amt,
                condition="DAMAGED"
            )
            db.add(bill_item)

            journal_tx = models.JournalTransaction(
                id=str(uuid.uuid4()),
                transaction_id=tx_id,
                user_id=partner_id,
                type="RETURN",
                bill_id=bill_id,
                product_id=None,
                product_name=product_name,
                quantity=return_qty,
                unit_price=product_price,
                total_amount=total_amt,
                previous_stock=None,
                new_stock=None,
                return_condition="DAMAGED",
                status="APPLIED",
                payload_json=json.dumps({
                    "demoIncidentId": DEMO_INCIDENT_ID,
                    "simulated": True,
                    "product_name": product_name,
                    "vendor_id": partner_id
                }),
                timestamp=tx_time,
                created_at=tx_time
            )
            db.add(journal_tx)
            injected_bills.append(bill_id)

        # Flush injected records so the query can detect them in current session
        db.flush()

        # 4. Run CIE Anomaly Detection
        # Condition: IF same product has returns from >= 5 different vendors within <= 30 minutes
        window_start = now - timedelta(minutes=30)
        recent_returns_query = db.query(
            models.Bill.user_id,
            models.BillItem.quantity
        ).join(
            models.BillItem, models.BillItem.bill_id == models.Bill.id
        ).filter(
            models.Bill.transaction_type == "RETURN",
            models.BillItem.product_name == product_name,
            models.Bill.created_at >= window_start
        ).all()

        distinct_vendors = len(set(r.user_id for r in recent_returns_query))
        total_returns = len(recent_returns_query)

        alert_record = None
        if distinct_vendors >= 5:
            # Exact wording mandated by requirements:
            # 🚨 CIE ALERT
            # Unusual cross-vendor return pattern detected.
            # Product: [product]
            # Affected vendors: [count]
            # Returns: [count]
            # Time window: [window]
            # Possible network-wide product issue.
            # Verification required.
            alert_msg = (
                f"🚨 CIE ALERT\n"
                f"Unusual cross-vendor return pattern detected.\n\n"
                f"Product: {product_name}\n"
                f"Affected vendors: {distinct_vendors}\n"
                f"Returns: {total_returns}\n"
                f"Time window: 25 minutes\n\n"
                f"Possible network-wide product issue.\n"
                f"Verification required."
            )

            alert_record = models.CIEAlert(
                id=str(uuid.uuid4()),
                incident_id=DEMO_INCIDENT_ID,
                product_name=product_name,
                affected_vendors_count=distinct_vendors,
                total_returns_count=total_returns,
                time_window_minutes=25,
                alert_title="Unusual cross-vendor return pattern detected",
                alert_message=alert_msg,
                status="ACTIVE",
                is_demo=1,
                created_at=now
            )
            db.add(alert_record)

        db.commit()

        return {
            "success": True,
            "incident_id": DEMO_INCIDENT_ID,
            "product_name": product_name,
            "affected_vendors_count": distinct_vendors,
            "total_returns_count": total_returns,
            "time_window_minutes": 25,
            "alert": {
                "id": alert_record.id if alert_record else None,
                "title": alert_record.alert_title if alert_record else "",
                "message": alert_record.alert_message if alert_record else "",
                "product_name": product_name,
                "affected_vendors": distinct_vendors,
                "returns_count": total_returns,
                "time_window": "25 minutes",
                "status": "ACTIVE"
            } if alert_record else None,
            "user_demo_bills": user_demo_bills_data,
            "has_active_incident": True,
            "message": f"Cross-Vendor Incident simulated for '{product_name}' across {distinct_vendors} partner stores."
        }

    @classmethod
    def reset_demo_incident(
        cls,
        db: Session,
        current_user_id: str
    ) -> Dict[str, Any]:
        """
        Targeted rollback:
        1. Deletes ONLY bills and items stamped with demoIncidentId = 'cross_vendor_return_demo'.
        2. Deletes ONLY journal records stamped with demoIncidentId = 'cross_vendor_return_demo'.
        3. Deletes ONLY CIE alerts stamped with incident_id = 'cross_vendor_return_demo'.
        4. NEVER touches real user returns, real bills, real stock, or production data.
        """
        # Step A: Find all demo bills
        demo_bills = db.query(models.Bill).filter(
            models.Bill.transaction_id.like(f"%{DEMO_INCIDENT_ID}%")
        ).all()
        demo_bill_ids = [b.id for b in demo_bills]

        # Step B: Delete demo bill items
        if demo_bill_ids:
            db.query(models.BillItem).filter(
                models.BillItem.bill_id.in_(demo_bill_ids)
            ).delete(synchronize_session=False)

        # Step C: Delete demo bills
        if demo_bill_ids:
            db.query(models.Bill).filter(
                models.Bill.id.in_(demo_bill_ids)
            ).delete(synchronize_session=False)

        # Step D: Delete demo journal entries
        db.query(models.JournalTransaction).filter(
            (models.JournalTransaction.transaction_id.like(f"%{DEMO_INCIDENT_ID}%")) |
            (models.JournalTransaction.payload_json.like(f"%{DEMO_INCIDENT_ID}%"))
        ).delete(synchronize_session=False)

        # Step E: Delete demo CIE alerts
        db.query(models.CIEAlert).filter(
            models.CIEAlert.incident_id == DEMO_INCIDENT_ID
        ).delete(synchronize_session=False)

        db.commit()

        return {
            "success": True,
            "message": "Demo incident reset successfully",
            "has_active_incident": False,
            "cleaned_bills_count": len(demo_bill_ids)
        }

    @classmethod
    def get_cie_status(
        cls,
        db: Session,
        current_user_id: str
    ) -> Dict[str, Any]:
        """
        Returns active CIE status and alerts.
        """
        active_alert = db.query(models.CIEAlert).filter(
            models.CIEAlert.status == "ACTIVE"
        ).order_by(desc(models.CIEAlert.created_at)).first()

        has_active = (active_alert is not None)

        return {
            "has_active_incident": has_active,
            "active_alert": {
                "id": active_alert.id,
                "incident_id": active_alert.incident_id,
                "product_name": active_alert.product_name,
                "affected_vendors_count": active_alert.affected_vendors_count,
                "total_returns_count": active_alert.total_returns_count,
                "time_window_minutes": active_alert.time_window_minutes,
                "alert_title": active_alert.alert_title,
                "alert_message": active_alert.alert_message,
                "status": active_alert.status,
                "created_at": str(active_alert.created_at)
            } if active_alert else None
        }


cie_manager = CIEManager()
