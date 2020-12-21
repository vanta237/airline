function toggleHeatmap() {
    if ($("#heatmapControlPanel").is(":visible")) {
        closeHeatmap()
    } else {
        showHeatmap()
    }
}

function showHeatmap() {
//    var showAlliance = $("#heatmapControlPanel .showAlliance").is(":checked")
    $('#heatmapControlPanel').data('cycleDelta', 0)
    $("#heatmapControlPanel").css('display', 'flex')

    var airlineId
    if (rivalMapAirlineId) {
      airlineId = rivalMapAirlineId
    } else {
      airlineId = activeAirline.id
    }

    if (airlineId) {
        updateHeatmap(airlineId)
    }
}

function closeHeatmap() {
    if (heatmap) {
        heatmap.setMap(null)
    }
     $("#heatmapControlPanel").hide()
}

var heatmap
const heatmapGradient = [
    "rgba(128, 133, 242, 0)",
    "rgba(141, 145, 243, 0.7)",
    "rgba(154, 157, 244, 0.7)",
    "rgba(167, 169, 245, 0.7)",
    "rgba(180, 181, 246, 0.7)",
    "rgba(193, 193, 247, 0.7)",
    "rgba(206, 205, 248, 0.7)",
    "rgba(219, 217, 249, 0.7)",
    "rgba(232, 229, 250, 0.7)",
    "rgba(245, 241, 251, 0.7)",
    "rgba(255, 255, 255, 1)",//
    "rgba(255, 253,  235, 1)",
    "rgba(255, 251, 215, 1)",
    "rgba(255, 249, 195, 1)",
    "rgba(255, 247, 175, 1)",
    "rgba(255, 245, 155, 1)",
    "rgba(255, 243, 135, 1)",
    "rgba(255, 241, 115, 1)",
    "rgba(255, 239, 95, 1)",
    "rgba(255, 238, 75, 1)",
    "rgba(255, 237, 52, 1)"
  ];

function updateHeatmap(airlineId) {
//    $.each(historyPaths, function(index, path) { //clear all history path
//        path.setMap(null)
//        path.shadowPath.setMap(null)
//    })

    if (heatmap) {
        heatmap.setMap(null) //clear previous one
    }
    var cycleDelta = $('#heatmapControlPanel').data('cycleDelta')
    var heatmapType = "loyalist"
    $.ajax({
        type: 'GET',
        url: "airlines/" + airlineId + "/heatmap-data?heatmapType=" + heatmapType + "&cycleDelta=" + cycleDelta,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            var heatmapData = []
            $.each(result.points, function(index, entry) {
              heatmapData.push({location: new google.maps.LatLng(entry.lat, entry.lng), weight: entry.weight})
            })

            heatmap = new google.maps.visualization.HeatmapLayer({
              data: heatmapData,
              dissipating: false,
              gradient: heatmapGradient,
              maxIntensity: result.maxIntensity,
              radius: 3
            });

            heatmap.setMap(map);

            updateHeatmapArrows(result.minDeltaCount, airlineId)
        },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
//	    ,
//	    beforeSend: function() {
//	    	$('body .loadingSpinner').show()
//	    },
//	    complete: function(){
//	    	$('body .loadingSpinner').hide()
//	    }
    });
}

function updateHeatmapArrows(minDeltaCount, airlineId) {
    var cycleDelta = $("#heatmapControlPanel").data('cycleDelta')
    var hasPrevPrev = true
    var hasPrev = true
    var hasNext = true
    var hasNextNext = true
    if (cycleDelta <= minDeltaCount) {
        hasPrev = false
    }
    if (cycleDelta - 10 < minDeltaCount) {
        hasPrevPrev = false
    }
    if (cycleDelta >= 0) {
        hasNext = false
    }
    if (cycleDelta + 10 > 0) {
        hasNextNext = false
    }

    $("#heatmapControlPanel span.cycleDeltaText").text(cycleDelta * (-10))
    $("#heatmapControlPanel img.prev").prop("onclick", null).off("click");
    if (!hasPrev) {
        $('#heatmapControlPanel img.prev').addClass('blackAndWhite')
        $('#heatmapControlPanel img.prev').removeClass('clickable')
    } else {
        $('#heatmapControlPanel img.prev').removeClass('blackAndWhite')
        $('#heatmapControlPanel img.prev').addClass('clickable')
        $("#heatmapControlPanel img.prev").click(function() {
            $("#heatmapControlPanel").data('cycleDelta', $("#heatmapControlPanel").data('cycleDelta') - 1)
            updateHeatmap(airlineId)
        })
    }
    $("#heatmapControlPanel img.prevPrev").prop("onclick", null).off("click");
    if (!hasPrevPrev) {
        $('#heatmapControlPanel img.prevPrev').addClass('blackAndWhite')
        $('#heatmapControlPanel img.prevPrev').removeClass('clickable')
    } else {
        $('#heatmapControlPanel img.prevPrev').removeClass('blackAndWhite')
        $('#heatmapControlPanel img.prevPrev').addClass('clickable')
        $("#heatmapControlPanel img.prevPrev").click(function() {
            $("#heatmapControlPanel").data('cycleDelta', $("#heatmapControlPanel").data('cycleDelta') - 10)
            updateHeatmap(airlineId)
        })
    }

    $("#heatmapControlPanel img.next").prop("onclick", null).off("click");
    if (!hasNext) {
        $('#heatmapControlPanel img.next').addClass('blackAndWhite')
        $('#heatmapControlPanel img.next').removeClass('clickable')
    } else {
        $('#heatmapControlPanel img.next').removeClass('blackAndWhite')
        $('#heatmapControlPanel img.next').addClass('clickable')
        $("#heatmapControlPanel img.next").click(function() {
            $("#heatmapControlPanel").data('cycleDelta', $("#heatmapControlPanel").data('cycleDelta') + 1)
            updateHeatmap(airlineId)
        })
    }

     $("#heatmapControlPanel img.nextNext").prop("onclick", null).off("click");
        if (!hasNextNext) {
            $('#heatmapControlPanel img.nextNext').addClass('blackAndWhite')
            $('#heatmapControlPanel img.nextNext').removeClass('clickable')
        } else {
            $('#heatmapControlPanel img.nextNext').removeClass('blackAndWhite')
            $('#heatmapControlPanel img.nextNext').addClass('clickable')
            $("#heatmapControlPanel img.nextNext").click(function() {
                $("#heatmapControlPanel").data('cycleDelta', $("#heatmapControlPanel").data('cycleDelta') + 10)
                updateHeatmap(airlineId)
            })
        }
}

