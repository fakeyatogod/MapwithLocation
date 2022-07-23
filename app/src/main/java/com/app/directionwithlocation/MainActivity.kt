package com.app.directionwithlocation

import android.Manifest
import android.annotation.SuppressLint
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.app.directionwithlocation.ui.theme.DirectionWithLocationTheme
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.LocationSource
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

private const val TAG = "Map App"

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private val locationSource = MyLocationSource()

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            when {
                permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {}
                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {}
                else -> {}
            }
        }

    }

    // ON CREATE
    // Bug in fusedLocation
    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )


        setContent {
            DirectionWithLocationTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Scaffold(topBar = {
                        TopAppBar(contentPadding = PaddingValues(horizontal = 16.dp)) {
                            Text(text = "Maps App For Location and Path")
                        }
                    }) { pad ->
                        val topPad = pad.calculateTopPadding()

                            var currentLocation: Location? by remember {
                                mutableStateOf(null)
                            }

                            locationCallback = object : LocationCallback() {
                                override fun onLocationResult(p0: LocationResult) {
                                    for (location in p0.locations) {
                                        currentLocation = location
                                        locationSource.onLocationChanged(location)
                                    }
                                }
                            }
                            locationRequest = LocationRequest.create().apply {
                                interval = TimeUnit.SECONDS.toMillis(10)
                                fastestInterval = TimeUnit.SECONDS.toMillis(5)
                                maxWaitTime = TimeUnit.SECONDS.toMillis(10)
                                priority = Priority.PRIORITY_HIGH_ACCURACY

                            }

                            fusedLocationClient.requestLocationUpdates(
                                locationRequest,
                                locationCallback,
                                Looper.myLooper()
                            )
                            if (currentLocation != null)
                                MapScreen(locationSource = locationSource, newLocation = currentLocation!!,topPad)

                    }
                }
            }
        }
    }


/*    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onResume() {
        super.onResume()
        startLocationUpdates()
    }*/
}

private class MyLocationSource : LocationSource {

    private var listener: LocationSource.OnLocationChangedListener? = null

    override fun activate(listener: LocationSource.OnLocationChangedListener) {
        this.listener = listener
    }

    override fun deactivate() {
        listener = null
    }

    fun onLocationChanged(location: Location) {
        listener?.onLocationChanged(location)
    }
}


@Composable
private fun MapScreen(
    locationSource: MyLocationSource,
    newLocation: Location,
    topPad: Dp
) {

    val loc = LatLng(newLocation.latitude, newLocation.longitude)
    locationSource.onLocationChanged(newLocation)
    var showMarker by remember { mutableStateOf(false) }
    var destinationLocation by remember { mutableStateOf(loc) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(loc, 14f)
    }

    var isMapLoaded by remember { mutableStateOf(false) }

    val uiSettings by remember { mutableStateOf(MapUiSettings()) }
    var properties by remember {
        mutableStateOf(MapProperties(mapType = MapType.NORMAL, isMyLocationEnabled = true))
    }


    // Update blue dot and camera when the location changes
    LaunchedEffect(newLocation) {
        Log.d(TAG, "Updating blue dot on map...")
        locationSource.onLocationChanged(newLocation)

        Log.d(TAG, "Updating camera position...")
        val cameraPosition = CameraPosition.fromLatLngZoom(
            LatLng(
                newLocation.latitude,
                newLocation.longitude
            ), cameraPositionState.position.zoom
        )
        cameraPositionState.animate(CameraUpdateFactory.newCameraPosition(cameraPosition), 1_000)
    }


    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.matchParentSize().padding(top = topPad),
            properties = properties,
            uiSettings = uiSettings,
            cameraPositionState = cameraPositionState,
            onMapLoaded = {
                isMapLoaded = true
            },
            onMapClick = {
                destinationLocation = it
                showMarker = true
                val url = getDirectionsUrl(
                    LatLng(newLocation.latitude, newLocation.longitude),
                    destinationLocation
                )
                println(url)
                /*
                {   "error_message" : "You must enable Billing on the Google Cloud Project at
                 https://console.cloud.google.com/project/_/billing/enable
                 Learn more at https://developers.google.com/maps/gmp-get-started",
                   "routes" : [],   "status" : "REQUEST_DENIED"}
                 */

                // Does not work because ABOVE
                CoroutineScope(Dispatchers.IO).launch{
                    val data = downloadUrl(url)
                    withContext(Dispatchers.Main){
                        println(data)
                    }
                }


            },
            locationSource = locationSource
        ) {
            if (showMarker) {
                Marker(
                    state = MarkerState(position = destinationLocation),
                    title = "Destination",
                    snippet = "${destinationLocation.latitude}, ${destinationLocation.longitude}"
                )
                Polyline(
                    points = listOf(
                        LatLng(newLocation.latitude, newLocation.longitude),
                        destinationLocation
                    )
                )
            }

        }

        // Buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 8.dp),
            horizontalAlignment = Alignment.End
        ) {
            Button(onClick = {
                properties = if (properties == properties.copy(mapType = MapType.NORMAL)) {
                    properties.copy(mapType = MapType.SATELLITE)
                } else {
                    properties.copy(mapType = MapType.NORMAL)
                }
            }
            ) {
                Text(text = "Toggle SATELLITE")
            }
        }

        if (!isMapLoaded) {
            AnimatedVisibility(
                modifier = Modifier
                    .matchParentSize(),
                visible = !isMapLoaded,
                enter = EnterTransition.None,
                exit = fadeOut()
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .background(MaterialTheme.colors.background)
                        .wrapContentSize()
                )
            }
        }
    }
}

private fun getDirectionsUrl(origin: LatLng, dest: LatLng): String {

    // Origin of route
    val strOrigin = "origin=" + origin.latitude + "," + origin.longitude

    // Destination of route
    val strDest = "destination=" + dest.latitude + "," + dest.longitude

    // Sensor enabled
    val sensor = "sensor=false"
    val mode = "mode=driving"
    // Building the parameters to the web service
    val parameters = "$strOrigin&$strDest&$sensor&$mode"

    // Output format
    val output = "json"

    // Building the url to the web service
    return "https://maps.googleapis.com/maps/api/directions/$output?$parameters&key=${BuildConfig.MAPS_API_KEY}"
}

private fun downloadUrl(strUrl: String): String {
    val iStream: InputStream
    val urlConnection: HttpURLConnection

    val url = URL(strUrl)

    urlConnection = url.openConnection() as HttpURLConnection
    urlConnection.connect()

    iStream = urlConnection.inputStream

    val br = BufferedReader(InputStreamReader(iStream))
    val sb = StringBuffer()

    var line = br.readLine()
    while (line != null) {
        sb.append(line)
        line = br.readLine()
    }

    val data = sb.toString()

    br.close()
    Log.d("data", data)
    iStream.close()
    urlConnection.disconnect()

    return data
}