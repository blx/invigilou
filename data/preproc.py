from datetime import datetime
from pyquery import PyQuery

UBC_EXAM_SCHEDULE_URL = "http://www.students.ubc.ca/coursesreg/exams/exam-schedule/"
UBC_EXAMS_HTML_SRC = "exam-schedule-2015w2.html"

def main(file=None, url=None):
    assert (file or url) and not (file and url)
    d = PyQuery(filename=file) if file else PyQuery(url=url)
    subject_tables = d("table.dataTable")

    courses = {}

    for table in subject_tables:
        c = courses[table.getprevious().text] = []
        for tr in table.findall("tr"):
            rows = [r.text_content().strip()    # strip cuts &nbsp; char
                    .replace("&nbspnbsp;", "")  # but not when mangled
                    .replace("&nnbsp;", "") or None
                    for r in tr.findall("td")[:4]]
            c.append({
                "code": rows[0],
                # Might not work for single-digit days, but irrelevant here
                "datetime": datetime.strptime(rows[1], "%b %d %Y %I:%M %p"),
                "location": rows[2].replace(" ", "").replace("\n", " "),
                "alphasplit": rows[3]
            })

    return courses


if __name__ == "__main__":
    main(url=UBC_EXAM_SCHEDULE_URL)
