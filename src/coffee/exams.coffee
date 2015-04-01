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

    self.init = ->
        #self.chart("#chart")

        n = document.getElementById("nextexams")
        n.innerHTML = moment(n.innerHTML, "YYYY-MM-DD HH:mm:ss").fromNow() + "."

    self
) (window.exams || {})

exams.init()
