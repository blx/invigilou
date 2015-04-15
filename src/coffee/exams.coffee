exams = ( (self) ->

    makeControls = (parentdiv) ->
        panel = d3.select parentdiv
        panel.append 'input'
            .attr 'type', 'text'
            .attr 'placeholder', "filter by..."

    self.doTime = (parentdiv) ->
        self.baserange = [
            self._exams[0].datetime
            self._exams[self._exams.length-1].datetime]
        chart = d3.chart.eventDrops()
            .width 930
            .margin
                top: 70, left: 30, bottom: 0, right: 30
            .start self.baserange[0]
            .end self.baserange[1]
            .hasLabels false
            .eventZoom _.debounce ((scl) -> self.map.updateFromScale scl, self._exams),
                                  150
        d3.select parentdiv
            .datum [{name: 'exams', dates: _(self._exams).pluck('datetime')}]
            .call chart
        
        d3.select parentdiv
            .append 'p'
            .text "(restore full range)"
            .attr 'class', 'rightbutton'
            .on "click", ->
                # TODO need to force update of zoom on eventdrops
                self.map.updateFromScale self.baserange, self._exams
        return

    self.doYears = (parentdiv, exams) ->
        width = 930
        height = 350

        x = d3.scale.linear()
            .range [0, width]
        y = d3.scale.linear()
            .range [height, 0]

        color = d3.scale.category10()

        xAxis = d3.svg.axis()
            .scale x
            .orient "bottom"

        yAxis = d3.svg.axis()
            .scale y
            .orient "left"

        line = d3.svg.line()
            .interpolate "basis"
            .x (d) -> x d.datetime
            .y (d) -> y d.count

        svg = d3.select parentdiv
            .append "svg"
            .attr "width", width
            .attr "height", height
            .append "g"
        
        # load data
        data = _(exams).chain()
            .groupBy "year"
            .map (group, yr) ->
                year: +yr
                values: _(group).chain()
                            .countBy "datetime"
                            .map (val, dt) ->
                                datetime: new Date dt
                                count: val
                            .value()
            .value()
        console.log data
        window.ben = data

        x.domain d3.extent exams, (d) -> d.datetime
        y.domain [0, d3.max data, (d) -> d3.max d.values, (d) -> d.count]
        color.domain d3.keys data

        svg.append "g"
            .attr "class", "x axis"
            .attr "transform", "translate(0,#{height})"
            .call xAxis

        svg.append "g"
            .attr "class", "y axis"
            .call yAxis

        courseyear = svg.selectAll ".courseyear"
            .data data
          .enter().append "g"
            .attr "class", "courseyear"

        courseyear.append "path"
            .attr "class", "line"
            .attr "d", (d) -> line d.values
            .style "stroke", (d) -> color d.year

        courseyear.append "text"
            .datum (d) ->
                name: d.year
                value: d.values[d.values.length - 1]
            .attr "transform", (d) -> "translate(#{x d.value.datetime},#{y d.value.count})"
            .attr "x", 3
            .attr "dy", ".35em"
            .text (d) -> d.name
        
        


    self.map = ((self) ->
        markerlayer = null
        heatlayer = null
        mapcontrol = null

        self.doMap = (parentdiv) ->
            tiles = L.tileLayer '//{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
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
                if scale.domain?
                    scale.domain()[0] <= x.datetime <= scale.domain()[1]
                else
                    scale[0] <= x.datetime <= scale[1]

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

            if heatlayer?
                heatlayer.setLatLngs latlngs
            else
                heatlayer = L.heatLayer latlngs,
                                        radius: 25
                                        minOpacity: .2
                                        max: 1.1
                    .addTo self.map

            if markerlayer?
                markerlayer.clearLayers()
                markerlayer.addLayer m for m in markers
            else
                markerlayer = L.layerGroup(markers).addTo self.map
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

        self.doTime '#times'
        makeControls '#controls'

        self.doYears '#years', self._exams

        self.map.doMap 'map'
        self.map.updateMap self._exams

    self
) (window.exams || {})

exams.init()
