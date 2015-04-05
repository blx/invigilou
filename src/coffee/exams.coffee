exams = ( (self) ->

    self.chart = (parentdiv) ->

        width = 700
        height = 500

        x = d3.scale.linear()
            .range([0, width])

        y = d3.scale.linear()
            .range([height, 0])

        svg = d3.select parentdiv
            .append "svg"
            .attr "width", width
            .attr "height", height

    self.doMap = (parentdiv) ->
        map = L.map parentdiv
            .setView [49.2651, -123.2522], 15

        L.tileLayer 'http://{s}.tile.osm.org/{z}/{x}/{y}.png',
                    attribution: '&copy; <a href="http://osm.org/copyright">OpenStreetMap</a> contributors'
            .addTo map

        latlngs = ([x.lat, x.lng] for x in self._exams when x.lat? and x.lng?)
        console.log latlngs
        L.heatLayer latlngs,
                    radius: 20
                    minOpacity: .2
            .addTo map

        return
        



    self.init = ->
        #self.chart("#chart")

        #n = document.getElementById("nextexams")
        #n.innerHTML = moment(n.innerHTML, "YYYY-MM-DD HH:mm:ss").fromNow() + "."

        self.doMap('map')

    self
) (window.exams || {})

exams.init()
