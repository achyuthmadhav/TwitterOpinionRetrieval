var app = angular.module('search', []);

app.controller("searchController", function($scope,$http,$window) {
    
    $scope.populate = function(){
        
          function geocodeAddress(response) {
            var geocoder = new google.maps.Geocoder();
            
                geocoder.geocode({'address': response.location}, function(results, status) {
                    if (status === 'OK') {
                      response.coordinates =  results[0].geometry.location;
                    } else {
                      console.log('Geocode was not successful for the following reason: ' + status);
                    }
                  });
            

          }
        console.log("http://localhost:8080/Project_war_exploded/search/lucene/"+$scope.query)
        var url = "http://localhost:8080/Project_war_exploded/search/lucene/"+$scope.query;
        $http.get(url).success( function(response) {
          $window.sessionStorage.setItem("data", JSON.stringify(response));
          localStorage["data"] = JSON.stringify(response);
            $scope.list = response;
            // for (tweet in response) {
            //     setTimeout(geocodeAddress(tweet), 1000)
                
            // }
        });
    };

    $scope.redirect = function() {
      $window.location.href = '/Users/harry/Desktop/Search/Map.html';
    }
});
