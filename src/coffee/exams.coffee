exams = ( (self) ->

    self.doTime = (parentdiv) ->
        chart = d3.chart.eventDrops()
            .width 1000
            .margin
                top: 70, left: 150, bottom: 20, right: 40
            .start new Date self._exams[0].datetime
            .end new Date self._exams[self._exams.length - 1].datetime
            .eventZoom (scl) -> self.map.updateFromScale scl, self._exams
        self.c = chart
        self.x = (new Date(x.datetime) for x in self._exams)
        d3.select parentdiv
            .datum [{name: "exams", dates: (new Date(x.datetime) for x in self._exams)}]
            .call chart
        return


    self.map = ((self) ->
        markerlayer = null
        heatlayer = null
        mapcontrol = null

        self.doMap = (parentdiv) ->
            tiles = L.tileLayer 'http://{s}.tile.osm.org/{z}/{x}/{y}.png',
                        attribution: '&copy; <a href="http://osm.org/copyright">OpenStreetMap</a> contributors'
            self.map = L.map parentdiv,
                center: [49.2651, -123.2522]
                zoom: 15
                layers: [tiles]

            mapcontrol = L.control.layers {}, {}
                .addTo self.map
            self.map
        
        self.updateFromScale = (scale, exams) ->
            self.updateMap _(exams).filter (x) ->
                scale.domain()[0] <= x.datetime <= scale.domain()[1]

        self.updateMap = (exams) ->
            return unless mapcontrol?
            lox = _(exams).countBy 'building'
            latlngs = []
            markers = []
            seen = []
            for x in exams
                # Collect all [lat, lon]s and make markers for each unique building.
                continue if not (x.lat? and x.lng?)
                latlngs.push [x.lat, x.lng]

                continue if x.shortcode in seen
                seen.push x.shortcode
                markers.push L.marker([x.lat, x.lng]).bindPopup "#{x.building}: #{lox[x.building]}"

            self.map.removeLayer heatlayer if heatlayer?
            heatlayer = L.heatLayer latlngs,
                        radius: 20
                        minOpacity: .2
                .addTo self.map

            if markerlayer?
                self.map.removeLayer markerlayer
                mapcontrol.removeLayer markerlayer
            markerlayer = L.layerGroup(markers).addTo(self.map)
            mapcontrol.addOverlay markerlayer, "Markers"

            return
        self
    ) {}
        


    self.init = ->
        #n = document.getElementById("nextexams")
        #n.innerHTML = moment(n.innerHTML, "YYYY-MM-DD HH:mm:ss").fromNow() + "."

        _(self._exams).each (x) ->
            x.datetime = new Date x.datetime
            return

        self.map.doMap('map')
        self.map.updateMap self._exams

        self.doTime('#times')

    self
) (window.exams || {})

exams.init()
