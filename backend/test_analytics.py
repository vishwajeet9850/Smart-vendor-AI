from database import SessionLocal
from routers.analytics import get_summary

db = SessionLocal()
users = ['uXXp4u9hvxP9hrv22LvllrlX6hx1', 'lV3NgaMqXxerZHZlLjjilXhJTL22', 'wmPdaGXXkGRfiglryt7T7QS0eWG3', 'harsh', 'demo_vendor_444']

for u in users:
    print(f"=== User: {u} ===")
    for days, range_type in [(30, '30days'), (7, '7days'), (0, 'today'), (1, 'yesterday')]:
        res = get_summary(user_id=u, days=days, range_type=range_type, db=db)
        print(f"  [{range_type}] Total Rev: {res.total_revenue}, Bills: {res.total_bills}, Top Prods: {len(res.top_products)}, Daily pts: {len(res.daily_revenue)}")

db.close()
