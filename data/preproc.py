from __future__ import print_function

import sqlite3
from datetime import datetime
from pyquery import PyQuery

UBC_EXAM_SCHEDULE_URL = "http://www.students.ubc.ca/coursesreg/exams/exam-schedule/"
UBC_EXAMS_HTML_SRC = "exam-schedule-2015w2.html"

SQLITE_FILE = "./exam-schedule.sqlite"

def db_connect():
    return sqlite3.connect(SQLITE_FILE)

def db_setup():
    db = db_connect()
    db.execute("""CREATE TABLE schedule_2014w2 (
        coursecode TEXT NOT NULL,
        datetime DATETIME,
        building TEXT,
        room TEXT,
        alphasplit TEXT,
        PRIMARY KEY (coursecode, alphasplit))""")
    db.execute("CREATE INDEX building_idx ON schedule_2014w2 (building)")
    db.close()

def insert(courses):
    db = db_connect()

    for code, sections in courses.iteritems():
        print("Inserting courses for %s" % code)
        data = []
        for s in sections:
            locsplit = s["location"].split(None, 1)
            data.append((s["code"], s["datetime"],
                         locsplit[0], locsplit[1] if len(locsplit) > 1 else "",
                         s["alphasplit"]))
        db.executemany("INSERT INTO schedule_2014w2 VALUES (?,?,?,?,?)",
                       data)
        print("Inserted %d sections" % len(sections))

    db.commit()
    db.close()

def main(file=None, url=None):
    assert (file or url) and not (file and url)
    d = PyQuery(filename=file) if file else PyQuery(url=url)
    subject_tables = d("table.dataTable")

    courses = {}

    for table in subject_tables:
        c = courses[table.getprevious().text] = []
        for tr in table.findall("tr"):
            rows = [r.text_content().strip()    # strip cuts &nbsp; chars
                    .replace("&nbspnbsp;", "")  # but not when mangled
                    .replace("&nnbsp;", "") or None
                    for r in tr.findall("td")[:4]]
            c.append({
                "code": rows[0],
                # Might not work for single-digit days, but irrelevant here:
                "datetime": datetime.strptime(rows[1], "%b %d %Y %I:%M %p"),
                "location": rows[2].replace(" ", "").replace("\n", " "),
                "alphasplit": rows[3]})

    return courses


if __name__ == "__main__":
    courses = main(url=UBC_EXAM_SCHEDULE_URL)
    #insert(courses)
