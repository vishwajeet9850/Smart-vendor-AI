import os
from sqlalchemy import create_engine, event
from sqlalchemy.orm import sessionmaker, DeclarativeBase

# Use explicit absolute path to smartvendor.db regardless of CMD working directory
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.path.join(BASE_DIR, "smartvendor.db")
DATABASE_URL = f"sqlite:///{DB_PATH}"

engine = create_engine(
    DATABASE_URL,
    connect_args={
        "check_same_thread": False,
        "timeout": 30
    }
)


@event.listens_for(engine, "connect")
def set_sqlite_pragma(dbapi_connection, connection_record):
    cursor = dbapi_connection.cursor()
    cursor.execute("PRAGMA journal_mode=WAL;")
    cursor.execute("PRAGMA synchronous=NORMAL;")
    cursor.execute("PRAGMA cache_size=-64000;")  # 64MB cache
    cursor.execute("PRAGMA busy_timeout=30000;")  # 30 second lock timeout
    cursor.close()


SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


class Base(DeclarativeBase):
    pass


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def run_migrations():
    """
    Safe idempotent SQLite migrations to ensure new columns exist without dropping data.
    """
    from sqlalchemy import text
    with engine.connect() as conn:
        try:
            res = conn.execute(text("PRAGMA table_info(bills);")).fetchall()
            col_names = [r[1] for r in res]
            if col_names and "transaction_type" not in col_names:
                conn.execute(text("ALTER TABLE bills ADD COLUMN transaction_type VARCHAR NOT NULL DEFAULT 'BILL';"))
                conn.commit()
        except Exception as e:
            print(f"Migration note for bills table: {e}")

        try:
            res_items = conn.execute(text("PRAGMA table_info(bill_items);")).fetchall()
            item_cols = [r[1] for r in res_items]
            if item_cols and "condition" not in item_cols:
                conn.execute(text("ALTER TABLE bill_items ADD COLUMN condition VARCHAR NOT NULL DEFAULT 'GOOD';"))
                conn.commit()
        except Exception as e:
            print(f"Migration note for bill_items table: {e}")


# Run migrations automatically on import
try:
    run_migrations()
except Exception:
    pass



