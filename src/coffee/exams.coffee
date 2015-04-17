exams = ( (self) ->

    self.domain = []
    inDomain = (d) ->
        self.domain[1] - d.datetime >= 0 and
        d.datetime - self.domain[0] >= 0

    makeControls = (parentdiv) ->
        panel = d3.select parentdiv
        ###
        panel.append "input"
            .attr "type", "text"
            .attr "placeholder", "filter by..."
        ###

    doTime = (parentdiv, onzoom) ->
        baserange = [self._exams[0].datetime,
                     self._exams[self._exams.length-1].datetime]
        self.domain = baserange

        chart = d3.chart.eventDrops()
            .width 930
            .margin
                top: 70, left: 30, bottom: 0, right: 30
            .start baserange[0]
            .end baserange[1]
            .hasLabels false
            .eventZoom _.debounce onzoom, 80

        d3.select parentdiv
            .datum [{name: "exams", dates: _(self._exams).pluck("datetime")}]
            .call chart
        
        ###
        d3.select parentdiv
            .append "p"
            .text "(restore full range)"
            .attr "class", "rightbutton"
            .on "click", ->
                # TODO need to force update of zoom on eventdrops
                self.map.updateFromScale baserange, self._exams
        ###

        return

    class Years
        constructor: (exams) ->
            @data = exams
            @x = d3.scale.linear()
            @y = d3.scale.linear()
            @color = d3.scale.category10()

            @xAxis = d3.svg.axis()
                .scale @x
                .orient "bottom"
                .tickFormat ""
            @yAxis = d3.svg.axis()
                .scale @y
                .orient "left"

            @line = d3.svg.line()
                .interpolate "basis"
                .x (d) -> @x d.datetime
                .y (d) -> @y d.count

        init: (parentdiv) ->
            @margin =
                top: 5
                left: 30
                right: 30
                bottom: 20
            @width = 930 - @margin.left - @margin.right
            @height = 200 - @margin.top - @margin.bottom

            @x.range [0, @width]
            @y.range [@height, 0]

            @svg = d3.select parentdiv
                .append "svg"
                .attr "width", @width + @margin.left + @margin.right
                .attr "height", @height + @margin.top + @margin.bottom
              .append "g"
                .attr "transform", "translate(#{@margin.left},#{@margin.top})"

            @gx = @svg.append "g"
                .attr "class", "x axis"
                .attr "transform", "translate(0,#{@height})"

            @gy = @svg.append "g"
                .attr "class", "y axis"

            @data = Years.preprocessor @data

            @y.domain [0, d3.max @data,
                                 (d) -> d3.max d.values,
                                               (d) -> d.count]
            @color.domain d3.keys @data

            @redraw()

        redraw: ->
            data = Years.filter self.domain, @data
            @render data

        @preprocessor: (data) ->
            _(data).chain()
                .groupBy "year"
                .map (group, yr) ->
                    year: +yr
                    values: _(group).chain()
                                .countBy "datetime"
                                .map (val, dt) ->
                                    datetime: new Date dt
                                    count: val
                                .value()
                .filter (d) ->
                    1 <= d.year <= 4
                .value()

        @filter: (domain, data) ->
            _(data).map (group) ->
                year: group.year
                values: _(group.values).filter inDomain

        render: (data) ->
            @x.domain self.domain

            @gx.call @xAxis
            @gy.call @yAxis

            @svg.selectAll ".courseyear"
                .remove()

            courseyear = @svg.selectAll ".courseyear"
                .data data
              .enter().append "g"
                .attr "class", "courseyear"

            courseyear.append "path"
                .attr "class", "line"
                .attr "d", (d) => @line d.values
                .style "stroke", (d) => @color d.year
                .style "stroke-width", (d) -> d.year * 0.8

            courseyear.append "text"
                .datum (d) ->
                    name: d.year
                    value: d.values[d.values.length - 1]
                .attr "transform", (d) => "translate(#{@x d.value.datetime},#{@y d.value.count})"
                .attr "x", 3
                .attr "dy", ".35em"
                .text (d) -> d.name


    class Map
        constructor: (exams, parentdiv) ->
            @exams = exams
            @markerlayer = null
            @heatlayer = null

            tiles = L.tileLayer '//{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
                                attribution: '&copy; <a href="http://osm.org/copyright">OpenStreetMap</a> contributors'
            @map = L.map parentdiv,
                         center: [49.2651, -123.2522]
                         zoom: 15
                         layers: [tiles]

            @mapcontrol = L.control.layers {}, {}
                .addTo @map

        filter: (exams) ->
            _(exams).filter inDomain

        render: ->
            exams = @filter @exams

            return unless @mapcontrol?
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

            if @heatlayer?
                @heatlayer.setLatLngs latlngs
            else
                @heatlayer = L.heatLayer latlngs,
                                         radius: 25
                                         minOpacity: .2
                                         max: 1.1
                    .addTo @map

            if @markerlayer?
                @markerlayer.clearLayers()
                @markerlayer.addLayer m for m in markers
            else
                @markerlayer = L.layerGroup(markers).addTo @map
                @mapcontrol.addOverlay @markerlayer, "Markers"

            return


    self.init = ->
        #n = document.getElementById("nextexams")
        #n.innerHTML = moment(n.innerHTML, "YYYY-MM-DD HH:mm:ss").fromNow() + "."

        _(self._exams).each (x) ->
            x.datetime = new Date x.datetime
            return

        ontimezoom = (newscale) ->
            self.domain = newscale.domain()
            self.map.render()
            self.years.redraw()

        doTime '#times', ontimezoom
        makeControls '#controls'

        self.years = new Years self._exams
        self.years.init '#years'

        self.map = new Map self._exams, 'map'
        self.map.render()
        return

    self
) (window.exams || {})

exams.init()
