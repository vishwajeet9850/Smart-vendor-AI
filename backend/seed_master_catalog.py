import os
import uuid
import random
from sqlalchemy.orm import Session
import models
from database import engine, SessionLocal

# Ensure tables exist
models.Base.metadata.create_all(bind=engine)

CATEGORIES_AND_ITEMS = [
    {
        "category": "Dairy & Bakery",
        "brands": ["Amul", "Mother Dairy", "Nandini", "Gowardhan", "Nestle", "Epigamia", "Britannia", "Milky Mist"],
        "items": ["Taaza Toned Milk", "Gold Full Cream Milk", "Fresh Dahi", "Paneer", "Butter", "Cheese Slices", "Cheese Block", "Flavoured Milk", "Fresh Cream", "Ghee", "Bread White", "Brown Bread"],
        "units": ["100g", "200g", "250g", "500g", "1kg", "200ml", "500ml", "1L"]
    },
    {
        "category": "Atta, Rice & Grains",
        "brands": ["Aashirvaad", "Fortune", "India Gate", "Kohinoor", "Daawat", "Tata Sampann", "Rajdhani", "Nature Fresh", "Patanjali"],
        "items": ["Shuddha Chakki Atta", "Multigrain Atta", "Basmati Rice", "Sona Masoori Rice", "Kolam Rice", "Moong Dal", "Toor Dal", "Chana Dal", "Urad Dal", "Rajma", "Kabuli Chana", "Sooji", "Maida", "Besan"],
        "units": ["500g", "1kg", "2kg", "5kg", "10kg"]
    },
    {
        "category": "Edible Oils & Spices",
        "brands": ["Fortune", "Dhara", "Saffola", "Gemini", "Everest", "MDH", "Catch", "Goldiee", "Tata Sampann", "Patanjali", "Sundrop"],
        "items": ["Sunflower Oil", "Mustard Oil", "Groundnut Oil", "Rice Bran Oil", "Pure Cow Ghee", "Garam Masala", "Turmeric Powder", "Red Chilli Powder", "Coriander Powder", "Cumin Seeds", "Chicken Masala", "Biryani Masala"],
        "units": ["100g", "200g", "500g", "500ml", "1L", "2L", "5L"]
    },
    {
        "category": "Biscuits & Cookies",
        "brands": ["Parle", "Britannia", "Sunfeast", "Priya Gold", "Unibic", "Cadbury", "Bikano"],
        "items": ["Parle-G", "Monaco", "Krackjack", "Hide & Seek", "Good Day Butter", "Good Day Cashew", "Marie Gold", "Bourbon", "NutriChoice Digestive", "Dark Fantasy Choco Fills", "Milk Bikis", "50-50 Maska Chaska"],
        "units": ["50g", "75g", "100g", "120g", "150g", "200g", "250g", "300g", "Pack of 4"]
    },
    {
        "category": "Snacks & Namkeen",
        "brands": ["Lay's", "Kurkure", "Bingo!", "Haldiram's", "Bikaji", "Doritos", "Pringles", "Uncle Chips", "Yellow Diamond", "Balaji"],
        "items": ["Classic Salted Chips", "Magic Masala Chips", "Cream & Onion Chips", "Masala Munch", "Solid Masti", "Tedhe Medhe", "Aloo Bhujia", "Moong Dal", "Khatte Meethe Namkeen", "Navratan Mixture", "Salted Peanuts", "Chana Jor Garam"],
        "units": ["20g", "35g", "50g", "85g", "100g", "150g", "200g", "400g"]
    },
    {
        "category": "Chocolates & Sweets",
        "brands": ["Cadbury", "Nestle", "Amul", "Ferrero", "Kinder", "Hershey's", "Mars"],
        "items": ["Dairy Milk Silk", "Dairy Milk Fruit & Nut", "5 Star", "Perk", "Gems", "KitKat", "Munch", "Milkybar", "Snickers", "Joy", "Choco Spread", "Gulab Jamun Can", "Rasgulla Can"],
        "units": ["10g", "20g", "40g", "50g", "100g", "150g", "500g", "1kg"]
    },
    {
        "category": "Beverages & Drinks",
        "brands": ["Coca-Cola", "Thums Up", "Sprite", "Limca", "Fanta", "Pepsi", "Seven Up", "Maaza", "Slice", "Frooti", "Real", "Paper Boat", "Red Bull", "Monster", "Tata Tea", "Red Label", "Taj Mahal", "Nescafe", "Bru"],
        "items": ["Carbonated Soft Drink", "Mango Drink", "Mixed Fruit Juice", "Apple Juice", "Aamras", "Energy Drink", "Gold Premium Tea", "Instant Coffee", "Classic Coffee", "Green Tea Bags"],
        "units": ["200ml", "250ml", "500ml", "600ml", "1.25L", "2L", "100g", "250g", "500g"]
    },
    {
        "category": "Noodles, Pasta & Instant Food",
        "brands": ["Maggi", "Yippee!", "Wai Wai", "Top Ramen", "Ching's Secret", "Knorr", "MTR", "Bambino", "Kissan"],
        "items": ["2-Minute Masala Noodles", "Mood Masala Noodles", "Schezwan Noodles", "Soupy Noodles", "Hakha Noodles", "Macaroni Pasta", "Tomato Ketchup", "Green Chilli Sauce", "Soy Sauce", "Instant Upma", "Instant Poha"],
        "units": ["70g", "140g", "280g", "500g", "1kg", "200g", "1kg Bottle"]
    },
    {
        "category": "Personal Care & Hygiene",
        "brands": ["Dettol", "Lifebuoy", "Dove", "Pears", "Lux", "Santoor", "Colgate", "Pepsodent", "Sensodyne", "Dabur", "Closeup", "Parachute", "Clinic Plus", "Sunsilk", "Head & Shoulders", "Nivea", "Himalaya", "Vaseline"],
        "items": ["Antiseptic Soap", "Moisturizing Soap", "Strong Teeth Toothpaste", "Red Toothpaste", "Fresh Gel Toothpaste", "Coconut Hair Oil", "Health Shampoo", "Anti Hairfall Shampoo", "Face Wash Purifying Neem", "Body Lotion", "Petroleum Jelly"],
        "units": ["75g", "100g", "125g", "150g", "200g", "100ml", "180ml", "340ml", "650ml"]
    },
    {
        "category": "Household & Cleaning",
        "brands": ["Surf Excel", "Tide", "Ariel", "Rin", "Wheel", "Vim", "Pril", "Colin", "Lysol", "Harpic", "Domex", "Good Knight", "All Out", "HIT"],
        "items": ["Easy Wash Detergent Powder", "Matic Liquid Detergent", "Dishwash Bar", "Dishwash Gel", "Glass Cleaner", "Disinfectant Surface Cleaner", "Power Plus Toilet Cleaner", "Mosquito Vaporizer Refill", "Cockroach Killer Spray"],
        "units": ["200g", "500g", "1kg", "2kg", "5kg", "250ml", "500ml", "1L"]
    }
]

MODIFIERS = [
    "Classic", "Premium", "Special", "Extra Large", "Value Pack", "Super Saver", 
    "Rich Cream", "Double Choco", "Zero Sugar", "Organic", "Natural", "Herbal",
    "Masala Magic", "Tangy Tomato", "Spicy Delight", "Golden", "Royal", "Original"
]

def seed_master_catalog():
    db: Session = SessionLocal()
    try:
        print("Starting Master Catalog 6,000 Seeding...")
        existing_count = db.query(models.MasterCatalog).count()
        print(f"Current items in Master Catalog: {existing_count}")

        if existing_count >= 6000:
            print("Master Catalog already has >= 6000 items. Skipping.")
            return

        needed = 6000 - existing_count
        items_to_add = []
        generated_set = set()
        random.seed(42)
        base_barcode = 8901000000000

        index = 0
        while len(items_to_add) < needed:
            cat_group = random.choice(CATEGORIES_AND_ITEMS)
            category = cat_group["category"]
            brand = random.choice(cat_group["brands"])
            item = random.choice(cat_group["items"])
            unit = random.choice(cat_group["units"])
            modifier = random.choice(MODIFIERS) if random.random() > 0.4 else ""

            if modifier:
                full_name = f"{brand} {modifier} {item} {unit}".strip()
            else:
                full_name = f"{brand} {item} {unit}".strip()

            if full_name in generated_set:
                continue

            generated_set.add(full_name)
            base_price = random.choice([10, 20, 30, 40, 50, 65, 80, 99, 120, 150, 199, 240, 299, 350, 450, 550, 799, 999])
            barcode_str = str(base_barcode + existing_count + index)

            catalog_item = models.MasterCatalog(
                id=str(uuid.uuid4()),
                name=full_name,
                category=category,
                suggested_price=float(base_price),
                barcode=barcode_str
            )
            items_to_add.append(catalog_item)
            index += 1

            if len(items_to_add) % 1000 == 0:
                db.add_all(items_to_add)
                db.commit()
                print(f"Inserted {len(items_to_add)} / {needed} into Master Catalog...")
                items_to_add = []

        if items_to_add:
            db.add_all(items_to_add)
            db.commit()

        # Also clean up demo_user products from store products table so user inventory is clean
        db.query(models.Product).filter(models.Product.user_id == "demo_user").delete(synchronize_session=False)
        db.commit()

        total_catalog = db.query(models.MasterCatalog).count()
        total_products = db.query(models.Product).count()
        print(f"🎉 DONE! Master Catalog Items: {total_catalog} | Actual Store Products: {total_products}")

    except Exception as e:
        db.rollback()
        print("Error seeding master catalog:", e)
    finally:
        db.close()

if __name__ == "__main__":
    seed_master_catalog()
