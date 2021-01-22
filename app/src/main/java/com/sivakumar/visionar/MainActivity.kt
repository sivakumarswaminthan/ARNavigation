package com.sivakumar.visionar

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mapbox.android.core.location.LocationEngineCallback
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.android.core.location.LocationEngineRequest
import com.mapbox.android.core.location.LocationEngineResult
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.Marker
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponent
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.LocationComponentOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute
import com.mapbox.vision.VisionManager.init
import com.mapbox.vision.utils.VisionLogger
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


class MainActivity : AppCompatActivity(), PermissionsListener, MapboxMap.OnMapClickListener,
    OnMapReadyCallback {


    companion object {
        private var TAG = MainActivity::class.java.simpleName
        private const val MAP_STYLE = "mapbox://styles/mapbox/dark-v10"
    }

    private var originPoint: Point? = null

    private lateinit var mapboxMap: MapboxMap

    private var destinationMarker: Marker? = null

    private var currentRoute: DirectionsRoute? = null
    private var navigationMapRoute: NavigationMapRoute? = null
    private var locationComponent: LocationComponent? = null

    lateinit var start_ar: Button
    lateinit var mapView: com.mapbox.mapboxsdk.maps.MapView



    override fun onExplanationNeeded(permissionsToExplain: MutableList<String>?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onPermissionResult(granted: Boolean) {
        if (granted) {

            // Permission sensitive logic called here, such as activating the Maps SDK's LocationComponent to show the device's location


        } else {

            // User denied the permission


        }
    }


    private val arLocationEngine by lazy {
        LocationEngineProvider.getBestLocationEngine(this)
    }

    private val arLocationEngineRequest by lazy {
        LocationEngineRequest.Builder(0)
            .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
            .build()
    }

    private val locationCallback by lazy {
        object : LocationEngineCallback<LocationEngineResult> {
            override fun onSuccess(result: LocationEngineResult?) {
                with(result as LocationEngineResult) {
                    originPoint = Point.fromLngLat(lastLocation?.longitude ?: .0, lastLocation?.latitude ?: .0)
                }
            }

            override fun onFailure(exception: Exception) {
            }
        }
    }

    var permissionsManager = PermissionsManager(this@MainActivity)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //setSupportActionBar(toolbar)

        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            // Permission sensitive logic called here, such as activating the Maps SDK's LocationComponent to show the device's location

        } else {
            permissionsManager = PermissionsManager(this)
            permissionsManager.requestLocationPermissions(this)
        }

        fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                                grantResults: IntArray) {
            permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }

        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            getPermission()
        }

        start_ar = findViewById<View>(R.id.start_ar) as Button
        mapView = findViewById<View>(R.id.mapView) as com.mapbox.mapboxsdk.maps.MapView


        mapView.onCreate(savedInstanceState)
        start_ar.setOnClickListener {
            if (currentRoute == null) {
                Toast.makeText(this, "Route is not ready yet!", Toast.LENGTH_LONG).show()
            } else {
                MapPage.directionsRoute = currentRoute
                MapPage.start(this)
            }
        }

        /**
         * navigate as per your requirement
         * Choose first option - if you are using an external camera
         * Choose second option - if you are using the device camera
         * comment the one which is not required
         */

        val intent = Intent(this@MainActivity, MapPage::class.java)
        val intent = Intent(this@MainActivity, MapPage1::class.java)

        startActivity(intent)

    }

    private fun getPermission() {
        if (ContextCompat.checkSelfPermission(this@MainActivity,
                Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {

            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this@MainActivity,
                    Manifest.permission.CAMERA)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this@MainActivity,
                    arrayOf(Manifest.permission.CAMERA),
                    50)

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        } else {
            // Permission has already been granted
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            50 -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return
            }

            // Add other 'when' lines to check for other
            // permissions this app might request.
            else -> {
                // Ignore all other requests.
            }
        }
    }






    override fun onStart() {
        super.onStart()
        mapView.onStart()
        mapView.getMapAsync(this)
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
        arLocationEngine.removeLocationUpdates(locationCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(0, 0)
    }

    override fun onMapClick(destination: LatLng): Boolean {
        destinationMarker?.let(mapboxMap::removeMarker)
        destinationMarker = mapboxMap.addMarker(MarkerOptions().position(destination))

        if (originPoint == null) {
            Toast.makeText(this, "Source location is not determined yet!", Toast.LENGTH_LONG).show()
            return false
        }

        getRoute(
            origin = originPoint!!,
            destination = Point.fromLngLat(destination.longitude, destination.latitude)
        )

        start_ar.visibility = View.VISIBLE

        return true
    }

    override fun onMapReady(mapboxMap: MapboxMap) {
        this.mapboxMap = mapboxMap
        mapboxMap.setStyle(Style.Builder().fromUri(MAP_STYLE)) {
            enableLocationComponent()
        }

        mapboxMap.addOnMapClickListener(this)
    }

    private fun getRoute(origin: Point, destination: Point) {
        NavigationRoute.builder(this)
            .accessToken(Mapbox.getAccessToken()!!)
            .origin(origin)
            .destination(destination)
            .build()
            .getRoute(object : Callback<DirectionsResponse> {
                override fun onResponse(call: Call<DirectionsResponse>, response: Response<DirectionsResponse>) {
                    if (response.body() == null || response.body()!!.routes().size < 1) {
                        return
                    }

                    currentRoute = response.body()!!.routes()[0]

                    // Draw the route on the map
                    if (navigationMapRoute != null) {
                        //navigationMapRoute!!.updateRouteVisibilityTo(false)
                        Toast.makeText(this@MainActivity, "Unable to set route visibility", Toast.LENGTH_LONG)
                    } else {
                        navigationMapRoute = NavigationMapRoute(null, mapView, mapboxMap, R.style.NavigationMapRoute)
                    }
                    navigationMapRoute!!.addRoute(currentRoute)
                }

                override fun onFailure(call: Call<DirectionsResponse>, throwable: Throwable) {}
            })
    }

    @SuppressLint("MissingPermission")
    private fun enableLocationComponent() {
        initializeLocationEngine()

        val locationComponentOptions = LocationComponentOptions.builder(this)
            .build()
        locationComponent = mapboxMap.locationComponent

        val locationComponentActivationOptions = LocationComponentActivationOptions
            .builder(this, mapboxMap.style!!)
            .locationEngine(arLocationEngine)
            .locationComponentOptions(locationComponentOptions)
            .build()

        locationComponent?.let {
            it.activateLocationComponent(locationComponentActivationOptions)
            it.isLocationComponentEnabled = true
            it.cameraMode = CameraMode.TRACKING
        }
    }

    @SuppressLint("MissingPermission")
    private fun initializeLocationEngine() {
        try {
            arLocationEngine.requestLocationUpdates(arLocationEngineRequest, locationCallback, mainLooper)
        } catch (se: SecurityException) {
            VisionLogger.d(TAG, se.toString())
        }

        arLocationEngine.getLastLocation(locationCallback)
    }
}
