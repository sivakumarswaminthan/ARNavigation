package com.sivakumar.visionar


import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.location.Location
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.android.core.location.LocationEngineCallback
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.android.core.location.LocationEngineRequest
import com.mapbox.android.core.location.LocationEngineResult
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.geojson.Point
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigation
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigationOptions
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute
import com.mapbox.services.android.navigation.v5.offroute.OffRouteListener
import com.mapbox.services.android.navigation.v5.route.RouteFetcher
import com.mapbox.services.android.navigation.v5.route.RouteListener
import com.mapbox.services.android.navigation.v5.routeprogress.ProgressChangeListener
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgress
import com.mapbox.vision.VisionManager
import com.mapbox.vision.ar.VisionArManager
import com.mapbox.vision.ar.core.models.Route
import com.mapbox.vision.ar.core.models.RoutePoint
import com.mapbox.vision.mobile.core.interfaces.VisionEventsListener
import com.mapbox.vision.mobile.core.models.*
import com.mapbox.vision.mobile.core.models.classification.FrameSignClassifications
import com.mapbox.vision.mobile.core.models.detection.FrameDetections
import com.mapbox.vision.mobile.core.models.frame.ImageFormat
import com.mapbox.vision.mobile.core.models.frame.ImageSize
import com.mapbox.vision.mobile.core.models.position.GeoCoordinate
import com.mapbox.vision.mobile.core.models.position.VehicleState
import com.mapbox.vision.mobile.core.models.road.RoadDescription
import com.mapbox.vision.mobile.core.models.world.WorldDescription
import com.mapbox.vision.performance.ModelPerformance
import com.mapbox.vision.performance.ModelPerformanceConfig
import com.mapbox.vision.performance.ModelPerformanceMode
import com.mapbox.vision.performance.ModelPerformanceRate
import com.mapbox.vision.utils.VisionLogger
import com.mapbox.vision.video.videosource.VideoSource
import com.mapbox.vision.video.videosource.VideoSourceListener
import com.mapbox.vision.view.VisionView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit


class MapPage : AppCompatActivity(),
     RouteListener, ProgressChangeListener,
    OffRouteListener {


    companion object {
        private var TAG = MapPage::class.java.simpleName

        private const val LOCATION_INTERVAL_DEFAULT = 0L
        private const val LOCATION_INTERVAL_FAST = 1000L

        var directionsRoute: DirectionsRoute? = null

        private const val VIDEO_FILE = "video.mp4"

        fun start(context: Activity) {
            context.startActivity(Intent(context, MapPage::class.java))
        }

        //lateinit var cameraParameters1: CameraParameters
        val cameraParameters1 = CameraParameters(500, 500, 150.0f, 150.0f)




    }


    // Handles navigation.
    private lateinit var mapboxNavigation: MapboxNavigation
    // Fetches route from points.
    private lateinit var routeFetcher: RouteFetcher
    private lateinit var lastRouteProgress: RouteProgress
    // var directionsRoute: DirectionsRoute = null

    lateinit var ar_view: com.mapbox.vision.ar.view.gl.VisionArView

    // This dummy points will be used to build route. For real world test this needs to be changed to real values for
    // source and target locations.
    private val ROUTE_ORIGIN = Point.fromLngLat(80.007072, 12.736914)
    private val ROUTE_DESTINATION = Point.fromLngLat(80.210064, 13.031197)


    lateinit var visionView: VisionView

    private val arLocationEngine by lazy {
        LocationEngineProvider.getBestLocationEngine(this)
    }

    private val locationCallback by lazy {
        object : LocationEngineCallback<LocationEngineResult> {
            override fun onSuccess(result: LocationEngineResult?) {
            }

            override fun onFailure(exception: Exception) {
            }
        }
    }



    private val arLocationEngineRequest by lazy {
        LocationEngineRequest.Builder(LOCATION_INTERVAL_DEFAULT)
            .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
            .setFastestInterval(LOCATION_INTERVAL_FAST)
            .build()
    }





    private var videoSourceListener: VideoSourceListener? = null

    private val handlerThread = HandlerThread("VideoDecode")

    // VideoSource that will play the file.
    private val customVideoSource = object : VideoSource {
        override fun attach(videoSourceListener: VideoSourceListener) {
            this@MapPage.videoSourceListener = videoSourceListener
            handlerThread.start()
            Handler(handlerThread.looper).post { startFileVideoSource() }
        }

        override fun detach() {
            videoSourceListener = null
            handlerThread.quitSafely()
        }
    }

    // VideoSourceListener handles events from Vision SDK.
    public val visionEventsListener = object : VisionEventsListener {

        override fun onAuthorizationStatusUpdated(authorizationStatus: AuthorizationStatus) {}

        override fun onFrameSegmentationUpdated(frameSegmentation: FrameSegmentation) {}

        override fun onFrameDetectionsUpdated(frameDetections: FrameDetections) {}

        override fun onFrameSignClassificationsUpdated(frameSignClassifications: FrameSignClassifications) {}

        override fun onRoadDescriptionUpdated(roadDescription: RoadDescription) {}

        override fun onWorldDescriptionUpdated(worldDescription: WorldDescription) {}

        override fun onVehicleStateUpdated(vehicleState: VehicleState) {}

        override fun onCameraUpdated(camera: Camera) {}

        override fun onCountryUpdated(country: Country) {}

        override fun onUpdateCompleted() {}
    }


    private fun startFileVideoSource() {


            val retriever = MediaMetadataRetriever()
            retriever.setDataSource("your-live-video-url",
                HashMap())


            // Get video frame size.
            val frameWidth = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))
            val frameHeight = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
            val imageSize = ImageSize(frameWidth, frameHeight)
            // ByteBuffer to hold RGBA bytes.
            val rgbaByteBuffer = ByteBuffer.wrap(ByteArray(frameWidth * frameHeight * 4))

            // Get duration.
            val duration = java.lang.Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))

            try {
                // Get frames one by one with 1 second intervals.
                for (seconds in 0 until duration) {
                    val bitmap = retriever
                        .getFrameAtTime(
                            TimeUnit.SECONDS.toMicros(seconds),
                            MediaMetadataRetriever.OPTION_CLOSEST
                        )
                        .copy(Bitmap.Config.ARGB_8888, false)

                    bitmap.copyPixelsToBuffer(rgbaByteBuffer)

                    videoSourceListener!!.onNewCameraParameters(cameraParameters1)
                    videoSourceListener!!.onNewFrame(
                        rgbaByteBuffer.array(),
                        ImageFormat.RGBA,
                        imageSize
                    )

                    rgbaByteBuffer.clear()
                }
            } catch (e: RuntimeException) {
                e.printStackTrace()
            } finally {
                try {
                    retriever.release()
                } catch (e: RuntimeException) {
                    e.printStackTrace()
                }

            }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onCreate(savedInstanceState)
        this.setContentView(R.layout.activity_ar_navigation)


        ar_view = findViewById<View>(R.id.ar_view) as com.mapbox.vision.ar.view.gl.VisionArView

        // Initialize navigation with your Mapbox access token.
        val builder = MapboxNavigationOptions
            .builder()
        mapboxNavigation = MapboxNavigation(
            this,
            getString(R.string.mapbox_access_token),
            builder.build()
        )

        // Initialize route fetcher with your Mapbox access token.
        routeFetcher = RouteFetcher(this, getString(R.string.mapbox_access_token))
        routeFetcher.addRouteListener(this)


    }

    override fun onResume() {
        super.onResume()

        try {
            arLocationEngine.requestLocationUpdates(
                arLocationEngineRequest,
                locationCallback,
                mainLooper
            )
        } catch (se: SecurityException) {
            VisionLogger.d(TAG, se.toString())
        }


        mapboxNavigation.addOffRouteListener(this)
        mapboxNavigation.addProgressChangeListener(this)


        VisionManager.create(customVideoSource)
        VisionManager.visionEventsListener = visionEventsListener
        VisionManager.start()


        VisionManager.setModelPerformanceConfig(
            ModelPerformanceConfig.Merged(
                performance = ModelPerformance.On(ModelPerformanceMode.DYNAMIC, ModelPerformanceRate.HIGH)
            )
        )


        VisionArManager.create(VisionManager)
        ar_view.setArManager(VisionArManager)
        ar_view.onResume()


        directionsRoute.let {
            if (it == null) {
                Toast.makeText(this, "Route is not set!", Toast.LENGTH_LONG).show()
                finish()
            } else {
                setRoute(it)
            }
        }



    }

    private fun initDirectionsRoute() {
        NavigationRoute.builder(this)
            .accessToken(getString(R.string.mapbox_access_token))
            .origin(ROUTE_ORIGIN)
            .destination(ROUTE_DESTINATION)
            .build()
            .getRoute(object : Callback<DirectionsResponse> {
                override fun onResponse(call: Call<DirectionsResponse>, response: Response<DirectionsResponse>) {
                    if (response.body() == null || response.body()!!.routes().isEmpty()) {
                        return
                    }

                    directionsRoute = response.body()!!.routes()[0]
                    mapboxNavigation.startNavigation(directionsRoute!!)

                    // Set route progress.
                    VisionArManager.setRoute(
                        Route(
                            directionsRoute!!.getRoutePoints(),
                            directionsRoute!!.duration()?.toFloat() ?: 0f,
                            "Source Location",
                            "Target Location"
                        )
                    )
                }

                override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {
                    t.printStackTrace()
                }
            })
    }

    override fun onPause() {
        super.onPause()
        ar_view.onPause()
        VisionArManager.destroy()
        VisionManager.stop()
        VisionManager.destroy()

        arLocationEngine.removeLocationUpdates(locationCallback)

        mapboxNavigation.removeProgressChangeListener(this)
        mapboxNavigation.removeOffRouteListener(this)
        mapboxNavigation.stopNavigation()
    }



    override fun onErrorReceived(throwable: Throwable?) {
        throwable?.printStackTrace()

        mapboxNavigation.stopNavigation()
        Toast.makeText(this, "Can not calculate the route requested", Toast.LENGTH_SHORT).show()
    }

    override fun onResponseReceived(response: DirectionsResponse, routeProgress: RouteProgress?) {
        mapboxNavigation.stopNavigation()
        if (response.routes().isEmpty()) {
            Toast.makeText(this, "Can not calculate the route requested", Toast.LENGTH_SHORT).show()
        }
        lastRouteProgress = routeProgress!!

        setRoute(response.routes()[0])
    }



    override fun onProgressChange(location: Location, routeProgress: RouteProgress) {
        lastRouteProgress = routeProgress
    }

    override fun userOffRoute(location: Location) {
        routeFetcher.findRouteFromRouteProgress(location, lastRouteProgress)
    }

    private fun DirectionsRoute.getRoutePoints(): Array<RoutePoint> {
        val routePoints = arrayListOf<RoutePoint>()
        legs()?.forEach { it ->
            it.steps()?.forEach { step ->
                val maneuverPoint = RoutePoint(
                    GeoCoordinate(
                        latitude = step.maneuver().location().latitude(),
                        longitude = step.maneuver().location().longitude()
                    )
                )
                routePoints.add(maneuverPoint)

                step.intersections()
                    ?.map {
                        RoutePoint(
                            GeoCoordinate(
                                latitude = step.maneuver().location().latitude(),
                                longitude = step.maneuver().location().longitude()
                            )
                        )
                    }
                    ?.let { stepPoints ->
                        routePoints.addAll(stepPoints)
                    }
            }
        }

        return routePoints.toTypedArray()
    }




    private fun setRoute(route: DirectionsRoute) {
        mapboxNavigation.startNavigation(route)

        VisionArManager.setRoute(Route(
            points = route.getRoutePoints(),
            eta = route.duration()?.toFloat() ?: 0f,
            sourceStreetName = "Source Location",
            targetStreetName = "Destination Location"
            )
        )
    }



}









