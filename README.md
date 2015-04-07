# invigiloù

When and where do exams happen? idk let's find out.

See on [Heroku](https://invigilou.herokuapp.com)!

## Prerequisites

You will need [Leiningen][] 2.0.0 or above installed.

We also use leiningen wrappers for Coffeescript, Jade, and Bower.

Check out `profile.clj` for the lowdown on dependencies. Not listed there is
[Leaflet.heat][].

[leiningen]: https://github.com/technomancy/leiningen
[Leaflet.heat]: https://github.com/Leaflet/Leaflet.heat

## (Setup) Fetching data

*To get exam schedule info:*
```
$ cd data
$ python
>>> import preproc
>>> preproc.db_setup()
>>> courses = preproc.main(url=preproc.UBC_EXAM_SCHEDULE_URL)
>>> preproc.insert(courses)
>>> exit()
```

*To get building info:*

Uncomment the `/createdb` and `/fetchaddresses` routes in
`src/invigilou/handler.clj`.

```
$ lein ring server-headless
```

Now navigate to `localhost:3000/createdb` followed by
`localhost:3000/fetchaddresses`.

This data now lives in data/exam-schedule.sqlite, which could've been stashed in the repo,
but honestly... binaries? in my repo? tsk tsk

## Building assets

If you watch your coffee closely enough, it will turn into Javascript:
```
$ lein coffee watch
```

## License

Copyright © 2015 Ben Cook
