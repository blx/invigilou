# invigiloù

When and where do exams happen? idk let's find out. Scroll or drag with your mouse on top of
the date range to adjust the time window seen in the map.

See on [Heroku](https://invigilou.herokuapp.com)!

## Dependencies

### Backend

The server code is in Clojure. You will need [Leiningen][] 2.0.0 or above 
installed.

Check out `project.clj` for the lowdown on Clojure dependencies.

[leiningen]: https://github.com/technomancy/leiningen

### Frontend

Frontend code is written in Coffeescript and other libraries are managed with
Bower. The standard install process for both of these is via NPM, the Node.js
package manager. If you have none of this installed, first install Node and then
run `npm install` in the invigilou directory.

If you already have Bower installed, just run `bower install` to install the
front-end libraries.

### Building the database (optional)

The exam- and building-database is already included as `data/exam-schedule.sqlite`,
but it can be rebuilt by doing the following:

0. Install Python and pip, if not already installed. Install Python dependencies:
```
$ pip install pyquery
```

1. Get exam schedule info:
```
$ cd data
$ python
>>> import preproc
>>> preproc.db_setup()
>>> courses = preproc.main(url=preproc.UBC_EXAM_SCHEDULE_URL)
>>> preproc.insert(courses)
>>> exit()
```

2. Get building info:

Uncomment the `/createdb` and `/fetchaddresses` routes in
`src/invigilou/handler.clj`.

```
$ lein ring server-headless
```

Now navigate to `localhost:3000/createdb` followed by
`localhost:3000/fetchaddresses`.


## Recompiling Coffeescript

If you watch your coffee closely enough, it will turn into Javascript:
```
$ coffee -o resources/public/js/ -cw src/coffee/
```

## Running

For development, with auto-reload on change of Clojure files:
```
$ lein ring server-headless
```

Compiled (as on Heroku), in the Jetty server:
```
$ lein with-profile uberjar uberjar
$ java -cp target/invigilou-standalone.jar clojure.main -m invigilou.handler
```

## License

Copyright © 2015 Ben Cook
