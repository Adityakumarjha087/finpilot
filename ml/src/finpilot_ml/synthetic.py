"""Synthetic bank-transaction generator.

Everything FinPilot trains, evaluates and seeds on is produced here. No real
customer data is ever used. The generator mimics the noise of real Indian bank
statements (UPI/POS/NEFT prefixes, reference numbers, truncation, random casing)
so the categoriser has to learn from messy narrations rather than clean names.
"""

from __future__ import annotations

import random
from dataclasses import asdict, dataclass
from datetime import date, datetime, timedelta

CATEGORIES: tuple[str, ...] = (
    "Food",
    "Groceries",
    "Rent",
    "Travel",
    "Transport",
    "Shopping",
    "Bills",
    "Entertainment",
    "Health",
    "Subscriptions",
    "Education",
    "Income",
    "Transfers",
)


@dataclass(frozen=True)
class Merchant:
    name: str
    category: str
    low: float
    high: float
    hours: tuple[int, int] = (9, 21)


def _m(category: str, low: float, high: float, hours: tuple[int, int], *names: str) -> list[Merchant]:
    return [Merchant(n, category, low, high, hours) for n in names]


MERCHANTS: list[Merchant] = [
    *_m(
        "Food",
        120,
        1400,
        (11, 23),
        "SWIGGY",
        "ZOMATO",
        "DOMINOS PIZZA",
        "STARBUCKS COFFEE",
        "MCDONALDS",
        "KFC RESTAURANT",
        "HALDIRAMS",
        "BARBEQUE NATION",
        "CAFE COFFEE DAY",
        "BURGER KING",
        "PIZZA HUT",
        "CHAAYOS CAFE",
        "SUBWAY",
        "THE BOMBAY CANTEEN",
        "PUNJABI DHABA",
        "BIRYANI BLUES",
        "TACO BELL",
        "BLUE TOKAI COFFEE",
        "FAASOS",
        "WOW MOMO",
    ),
    *_m(
        "Groceries",
        250,
        4500,
        (8, 22),
        "BIGBASKET",
        "BLINKIT",
        "ZEPTO",
        "DMART",
        "RELIANCE FRESH",
        "MORE SUPERMARKET",
        "NATURES BASKET",
        "SPENCERS RETAIL",
        "JIOMART GROCERY",
        "STAR BAZAAR",
        "LOCAL KIRANA STORE",
        "SPAR HYPERMARKET",
        "FRESH MART",
        "TOTAL MART",
        "DAILY NEEDS GROCERY",
    ),
    *_m(
        "Rent",
        18000,
        32000,
        (8, 12),
        "RENT PAYMENT LANDLORD",
        "NOBROKER RENT",
        "HOUSING RENT PAY",
        "MAGICBRICKS RENTPAY",
        "FLAT RENT TRANSFER",
        "PG ACCOMMODATION RENT",
        "RENT CRED",
    ),
    *_m(
        "Travel",
        2500,
        28000,
        (6, 23),
        "MAKEMYTRIP",
        "INDIGO AIRLINES",
        "AIR INDIA",
        "IRCTC RAIL",
        "GOIBIBO",
        "OYO ROOMS",
        "TAJ HOTELS",
        "CLEARTRIP",
        "VISTARA AIR",
        "AIRBNB",
        "YATRA TRAVEL",
        "EASEMYTRIP",
        "MARRIOTT HOTEL",
        "IXIGO TRAINS",
    ),
    *_m(
        "Transport",
        60,
        900,
        (6, 23),
        "UBER",
        "OLA CABS",
        "RAPIDO BIKE",
        "INDIAN OIL FUEL",
        "HP PETROL PUMP",
        "BHARAT PETROLEUM FUEL",
        "DELHI METRO RAIL",
        "FASTAG TOLL",
        "NAMMA YATRI",
        "BMTC BUS PASS",
        "SHELL FUEL STATION",
        "MUMBAI METRO",
        "BLUSMART CAB",
    ),
    *_m(
        "Shopping",
        400,
        9000,
        (10, 23),
        "AMAZON IN",
        "FLIPKART",
        "MYNTRA",
        "AJIO",
        "NYKAA",
        "DECATHLON SPORTS",
        "IKEA",
        "CROMA ELECTRONICS",
        "RELIANCE DIGITAL",
        "LIFESTYLE STORES",
        "H AND M",
        "ZARA",
        "TATA CLIQ",
        "MEESHO",
        "SHOPPERS STOP",
        "UNIQLO",
    ),
    *_m(
        "Bills",
        300,
        4200,
        (9, 20),
        "BESCOM ELECTRICITY",
        "TATA POWER",
        "AIRTEL POSTPAID",
        "JIO RECHARGE",
        "ACT FIBERNET BROADBAND",
        "BSNL BILL",
        "MAHANAGAR GAS",
        "WATER BOARD BILL",
        "VI POSTPAID",
        "ADANI ELECTRICITY",
        "HATHWAY BROADBAND",
        "INDANE GAS CYLINDER",
        "SOCIETY MAINTENANCE",
    ),
    *_m(
        "Entertainment",
        150,
        2500,
        (12, 23),
        "BOOKMYSHOW",
        "PVR CINEMAS",
        "INOX MOVIES",
        "STEAM GAMES",
        "PLAYSTATION STORE",
        "SMAAASH GAMING",
        "TIMEZONE ARCADE",
        "WONDERLA PARK",
        "PAYTM INSIDER EVENTS",
        "CINEPOLIS",
        "BOWLING ALLEY",
        "COMEDY CLUB TICKETS",
    ),
    *_m(
        "Health",
        150,
        6000,
        (8, 21),
        "APOLLO PHARMACY",
        "PHARMEASY",
        "NETMEDS",
        "PRACTO CONSULT",
        "MAX HOSPITAL",
        "FORTIS HEALTHCARE",
        "1MG PHARMACY",
        "CULT FIT GYM",
        "MEDPLUS PHARMACY",
        "DR LAL PATHLABS",
        "THYROCARE LAB",
        "DENTAL CLINIC",
        "WELLNESS FOREVER CHEMIST",
    ),
    *_m(
        "Subscriptions",
        99,
        1500,
        (0, 6),
        "NETFLIX",
        "SPOTIFY",
        "AMAZON PRIME",
        "DISNEY HOTSTAR",
        "YOUTUBE PREMIUM",
        "APPLE ICLOUD",
        "GOOGLE ONE STORAGE",
        "LINKEDIN PREMIUM",
        "SONYLIV",
        "ZEE5 SUBSCRIPTION",
        "NOTION LABS",
        "MICROSOFT 365",
        "AUDIBLE",
        "CHATGPT PLUS",
        "CANVA PRO",
    ),
    *_m(
        "Education",
        500,
        12000,
        (9, 21),
        "COURSERA",
        "UDEMY",
        "BYJUS",
        "UNACADEMY",
        "UPGRAD",
        "SCHOOL FEES",
        "UNIVERSITY TUITION",
        "SIMPLILEARN",
        "CODING NINJAS",
        "KINDLE BOOKS",
        "CROSSWORD BOOKSTORE",
        "SCALER ACADEMY",
    ),
    *_m(
        "Income",
        3000,
        160000,
        (8, 12),
        "SALARY ACME TECHNOLOGIES",
        "SALARY CREDIT",
        "INTEREST CREDIT",
        "DIVIDEND CREDIT",
        "FREELANCE PAYMENT UPWORK",
        "REFUND AMAZON",
        "CASHBACK CREDIT",
        "BONUS PAYOUT",
        "PAYROLL INFOSYS",
        "TAX REFUND ITD",
    ),
    *_m(
        "Transfers",
        500,
        25000,
        (9, 22),
        "SELF TRANSFER",
        "TRANSFER TO SAVINGS",
        "SIP MUTUAL FUND",
        "ZERODHA BROKING",
        "GROWW INVEST",
        "CREDIT CARD PAYMENT",
        "PPF DEPOSIT",
        "RD INSTALMENT",
        "SENT TO FRIEND",
        "FAMILY TRANSFER",
    ),
]

INCOME_CATEGORIES = frozenset({"Income"})
CITIES = ("BANGALORE", "MUMBAI", "DELHI", "PUNE", "HYDERABAD", "CHENNAI", "KOLKATA", "GURGAON", "NOIDA")
TEMPLATES = (
    "UPI/{m}/{ref}/Payment",
    "UPI-{m}-{vpa}@okhdfc-{ref}",
    "POS {card} {m} {city}",
    "{m} {city} IN",
    "ACH D- {m}-{ref}",
    "NEFT-{ref}-{m}",
    "CARD PURCHASE {m}",
    "{m}*{short}",
    "IMPS/{ref}/{m}",
    "{m}",
    "BIL/ONL/{ref}/{m}",
    "VIN/{m}/{date}/{ref}",
)


def _noisy(text: str, rng: random.Random) -> str:
    r = rng.random()
    if r < 0.15:
        text = text.lower()
    elif r < 0.25:
        text = text.title()
    if rng.random() < 0.1 and len(text) > 22:
        text = text[: rng.randint(18, len(text))]
    return text


def describe(merchant: Merchant, rng: random.Random, when: date | None = None) -> str:
    """Render a realistic, noisy bank narration for a merchant."""
    template = rng.choice(TEMPLATES)
    name = merchant.name
    if rng.random() < 0.2:
        name = name.replace(" ", "")
    text = template.format(
        m=name,
        ref=rng.randint(10**9, 10**12 - 1),
        vpa=name.lower().replace(" ", "")[:10],
        card=f"{rng.randint(4000, 5999)}XXXXXX{rng.randint(1000, 9999)}",
        city=rng.choice(CITIES),
        short=f"{rng.randint(100000, 999999)}",
        date=(when or date(2026, 1, 1)).strftime("%d%m%y"),
    )
    return _noisy(text, rng)


@dataclass
class SyntheticTransaction:
    posted_at: str
    description: str
    merchant: str
    amount: float
    category: str
    is_anomaly: bool = False

    def as_dict(self) -> dict[str, object]:
        return asdict(self)


def labelled_corpus(
    n_per_merchant: int = 30, seed: int = 7, merchants: list[Merchant] | None = None
) -> list[tuple[str, float, str, str]]:
    """Return (description, amount, category, merchant) tuples for model training/evaluation."""
    rng = random.Random(seed)
    rows: list[tuple[str, float, str, str]] = []
    for merchant in merchants or MERCHANTS:
        for _ in range(n_per_merchant):
            amount = round(rng.uniform(merchant.low, merchant.high), 2)
            signed = amount if merchant.category in INCOME_CATEGORIES else -amount
            rows.append((describe(merchant, rng), signed, merchant.category, merchant.name))
    rng.shuffle(rows)
    return rows


def _at(day: date, merchant: Merchant, rng: random.Random) -> datetime:
    lo, hi = merchant.hours
    hour = rng.randint(lo, max(lo, hi - 1))
    return datetime(day.year, day.month, day.day, hour, rng.randint(0, 59), rng.randint(0, 59))


def _by_category(category: str) -> list[Merchant]:
    return [m for m in MERCHANTS if m.category == category]


def user_history(
    months: int = 12,
    seed: int = 42,
    end: date | None = None,
    anomaly_rate: float = 0.012,
) -> list[SyntheticTransaction]:
    """Generate a coherent month-by-month history for one persona.

    The persona earns a monthly salary, pays rent, holds a fixed set of subscriptions
    (so recurring detection has something to find) and spends in everyday categories
    with weekly seasonality. A small fraction of injected anomalies is labelled
    ``is_anomaly=True`` for evaluation.
    """
    rng = random.Random(seed)
    end = end or date.today()
    first_month = date(end.year, end.month, 1)
    for _ in range(months - 1):
        first_month = (first_month - timedelta(days=1)).replace(day=1)

    persona_food = rng.sample(_by_category("Food"), 6)
    persona_groc = rng.sample(_by_category("Groceries"), 3)
    persona_trans = rng.sample(_by_category("Transport"), 4)
    persona_shop = rng.sample(_by_category("Shopping"), 6)
    persona_ent = rng.sample(_by_category("Entertainment"), 4)
    persona_health = rng.sample(_by_category("Health"), 3)
    subs = rng.sample(_by_category("Subscriptions"), 4)
    sub_prices = {s.name: rng.choice([129.0, 149.0, 199.0, 299.0, 499.0, 649.0]) for s in subs}
    bills = rng.sample(_by_category("Bills"), 3)
    salary = round(rng.uniform(95000, 140000), -2)
    rent = round(rng.uniform(20000, 30000), -3)
    rent_m = rng.choice(_by_category("Rent"))
    salary_m = _by_category("Income")[0]

    out: list[SyntheticTransaction] = []

    def add(
        day: date, merchant: Merchant, amount: float, anomaly: bool = False, when: datetime | None = None
    ) -> None:
        if day > end:
            return
        signed = abs(amount) if merchant.category in INCOME_CATEGORIES else -abs(amount)
        ts = when or _at(day, merchant, rng)
        out.append(
            SyntheticTransaction(
                posted_at=ts.isoformat(),
                description=describe(merchant, rng, day),
                merchant=merchant.name,
                amount=round(signed, 2),
                category=merchant.category,
                is_anomaly=anomaly,
            )
        )

    month = first_month
    month_index = 0
    while month <= end:
        nxt = (month.replace(day=28) + timedelta(days=4)).replace(day=1)
        days = [month + timedelta(days=i) for i in range((nxt - month).days)]
        seasonal = 1.0 + 0.15 * (month.month in (10, 11, 12)) + 0.02 * month_index
        add(month, salary_m, salary)
        add(days[min(2, len(days) - 1)], rent_m, rent)
        for i, s in enumerate(subs):
            add(days[min(4 + i * 3, len(days) - 1)], s, sub_prices[s.name])
        for i, b in enumerate(bills):
            add(days[min(8 + i * 4, len(days) - 1)], b, rng.uniform(b.low, b.low + (b.high - b.low) * 0.5))
        for day in days:
            weekend = day.weekday() >= 5
            if rng.random() < (0.55 if weekend else 0.35):
                f = rng.choice(persona_food)
                add(day, f, rng.uniform(f.low, f.low + (f.high - f.low) * 0.45) * seasonal)
            if rng.random() < 0.5:
                t = rng.choice(persona_trans)
                add(day, t, rng.uniform(t.low, t.high * 0.6))
            if day.weekday() == 6 or rng.random() < 0.05:
                g = rng.choice(persona_groc)
                add(day, g, rng.uniform(800, 3200) * seasonal)
            if rng.random() < 0.08 * seasonal:
                s = rng.choice(persona_shop)
                add(day, s, rng.uniform(s.low, s.high * 0.5))
            if weekend and rng.random() < 0.25:
                e = rng.choice(persona_ent)
                add(day, e, rng.uniform(e.low, e.high * 0.6))
            if rng.random() < 0.03:
                h = rng.choice(persona_health)
                add(day, h, rng.uniform(h.low, h.high * 0.4))
            if rng.random() < anomaly_rate:
                category = rng.choice(["Shopping", "Travel", "Food", "Entertainment"])
                pool = _by_category(category)
                m = rng.choice(pool)
                hour = rng.choice([1, 2, 3, 4])
                when = datetime(day.year, day.month, day.day, hour, rng.randint(0, 59))
                add(day, m, m.high * rng.uniform(2.5, 6.0), anomaly=True, when=when)
        if month.month in (3, 9):
            tr = rng.choice(_by_category("Travel"))
            add(days[rng.randint(5, 20)], tr, rng.uniform(tr.low * 2, tr.high * 0.7))
        add(days[min(25, len(days) - 1)], _by_category("Transfers")[2], 10000)
        month = nxt
        month_index += 1

    out.sort(key=lambda t: t.posted_at)
    return out
