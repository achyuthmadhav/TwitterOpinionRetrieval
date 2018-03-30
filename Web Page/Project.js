$(document).ready(function () {
    var itemsMainDiv = ('.MultiCarousel');
    var itemsDiv = ('.MultiCarousel-inner');
    var itemWidth = "";

    $('.leftLst, .rightLst').click(function () {
        var condition = $(this).hasClass("leftLst");
        if (condition)
            click(0, this);
        else
            click(1, this)
    });

    ResCarouselSize();

    $(window).resize(function () {
        ResCarouselSize();
    });

    //this function define the size of the items
    function ResCarouselSize() {
        var incno = 0;
        var dataItems = ("data-items");
        var itemClass = ('.item');
        var id = 0;
        var btnParentSb = '';
        var itemsSplit = '';
        var sampwidth = $(itemsMainDiv).width();
        var bodyWidth = $('body').width();
        $(itemsDiv).each(function () {
            id = id + 1;
            var itemNumbers = $(this).find(itemClass).length;
            btnParentSb = $(this).parent().attr(dataItems);
            itemsSplit = btnParentSb.split(',');
            $(this).parent().attr("id", "MultiCarousel" + id);


            if (bodyWidth >= 1200) {
                incno = itemsSplit[3];
                itemWidth = sampwidth / incno;
            }
            else if (bodyWidth >= 992) {
                incno = itemsSplit[2];
                itemWidth = sampwidth / incno;
            }
            else if (bodyWidth >= 768) {
                incno = itemsSplit[1];
                itemWidth = sampwidth / incno;
            }
            else {
                incno = itemsSplit[0];
                itemWidth = sampwidth / incno;
            }
            $(this).css({ 'transform': 'translateX(0px)', 'width': itemWidth * itemNumbers });
            $(this).find(itemClass).each(function () {
                $(this).outerWidth(itemWidth);
            });

            $(".leftLst").addClass("over");
            $(".rightLst").removeClass("over");

        });
    }


    //this function used to move the items
    function ResCarousel(e, el, s) {
        var leftBtn = ('.leftLst');
        var rightBtn = ('.rightLst');
        var translateXval = '';
        var divStyle = $(el + ' ' + itemsDiv).css('transform');
        var values = divStyle.match(/-?[\d\.]+/g);
        var xds = Math.abs(values[4]);
        if (e == 0) {
            translateXval = parseInt(xds) - parseInt(itemWidth * s);
            $(el + ' ' + rightBtn).removeClass("over");

            if (translateXval <= itemWidth / 2) {
                translateXval = 0;
                $(el + ' ' + leftBtn).addClass("over");
            }
        }
        else if (e == 1) {
            var itemsCondition = $(el).find(itemsDiv).width() - $(el).width();
            translateXval = parseInt(xds) + parseInt(itemWidth * s);
            $(el + ' ' + leftBtn).removeClass("over");

            if (translateXval >= itemsCondition - itemWidth / 2) {
                translateXval = itemsCondition;
                $(el + ' ' + rightBtn).addClass("over");
            }
        }
        $(el + ' ' + itemsDiv).css('transform', 'translateX(' + -translateXval + 'px)');
    }

    //It is used to get some elements from btn
    function click(ell, ee) {
        var Parent = "#" + $(ee).parent().attr("id");
        var slide = $(Parent).attr("data-slide");
        ResCarousel(ell, Parent, slide);
    }

});



var app = angular.module('search', []);

app.controller("searchController", function($scope,$http,$window) {
    
    $scope.populate = function(){
        console.log("http://localhost:8080/Project_war_exploded/search/lucene/"+$scope.query)
        var url = "http://localhost:8080/Project_war_exploded/search/lucene/"+encodeURIComponent($scope.query);
        $http.get(url).success( function(response) {
            $scope.retweeted = response.mostRetweeted;
          var resp = response.response;
          $window.sessionStorage.setItem("data", JSON.stringify(resp));
          resp.sort(function(a, b){
            return a.score - b.score;
        });
          localStorage["data"] = JSON.stringify(resp);
            $scope.list = resp;
            $scope.toShow = true;
        });
    };

    $scope.populateHadoop = function(){
        console.log("http://localhost:8080/Project_war_exploded/search/hadoop/"+$scope.query)
        var url = "http://localhost:8080/Project_war_exploded/search/hadoop/"+encodeURIComponent($scope.query);
        $http.get(url).success( function(response) {
            var resp = response.response;
            resp.sort(function(a, b){
                return a.score - b.score;
            });
          $window.sessionStorage.setItem("data", JSON.stringify(resp));
          localStorage["data"] = JSON.stringify(resp);
            $scope.list = resp;
            $scope.toShow = true;
        });
    };

    $scope.redirect = function() {
      //$window.location.href = '/Users/harry/Desktop/Search/Map.html';
      var returnJson  = localStorage["data"];
        //console.log(returnJson);
        var t = JSON.parse(returnJson);
        console.log(t);
        var bombMap = new Datamap({
    element: document.getElementById('map_bombs'),
    scope: 'world',
    geographyConfig: {
        popupOnHover: false,
        highlightOnHover: false
    },
    fills: {
        'STRONGPOSITIVE': '#28B463',
        'WEAKPOSITIVE': '#82E0AA',
        'STRONGNEGATIVE': '#CB4335',
        'WEAKNEGATIVE': '#F1948A',
        'NEUTRAL':'#D5DBDB',
        'WHITE':'#ffffff',
        defaultFill: '#EDDC4E'
    }
    });
    var bombs = [];
    for(var i = 0; i < t.length; i++) {
        var obj = t[i];
        var twt = {}
        twt.name = obj.name;
        twt.yield = obj.tweetBody;
        twt.radius = 10;
        switch(obj.sentiment) {
            case 4: twt.fillKey = 'STRONGPOSITIVE'; break;
            case 3: twt.fillKey = 'WEAKPOSITIVE'; break;
            case 2: twt.fillKey = 'NEUTRAL'; break;
            case 1: twt.fillKey = 'WEAKNEGATIVE'; break;
            case 0: twt.fillKey = 'STRONGNEGATIVE'; break;
            default: twt.fillKey = 'WHITE'; break;
        }
        twt.date = obj.created;
        twt.location = obj.location;
        twt.latitude = obj.latitude;
        twt.longitude = obj.longitude;
        bombs.push(twt);
    }
    console.log(bombs);
   
//draw bubbles for bombs
bombMap.bubbles(bombs, {
    popupTemplate: function (geo, data) {
            return ['<div class="hoverinfo">' +  data.name,
            '<br/>Tweet: ' +  data.yield,
            '<br/>Location: ' +  data.location + '',
            '<br/>Date: ' +  data.date + '',
            '</div>'].join('');
    }
});
$("#map_bombs").dialog({
        resizable: false,
        modal: true,
        width:'auto'
});

    }
});
