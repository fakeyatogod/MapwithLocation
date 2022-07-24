package com.app.directionwithlocation

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
private val locationSource = MyLocationSource()
var currentLocation: MutableState<Location?> = mutableStateOf(null)
private var shouldShowMap: MutableState<Boolean> = mutableStateOf(false)

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    @Composable
    fun RequestPerms() {
        val requestPermissionLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                ) {
                    Log.i("kilo", "Permission granted")
                    setupFusedLocation()
                    shouldShowMap.value = true
                } else {
                    Log.i("kilo", "Permission denied")
                }
            }

        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.i("kilo", "Permission previously granted")
                shouldShowMap.value = true
            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.CAMERA
            ) -> Log.i("kilo", "Show camera permissions dialog")

            else -> SideEffect {
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }


    // ON CREATE
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DirectionWithLocationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    ScaffoldWithTopBar()
                    RequestPerms()
                }
            }
        }

    }

    // Bug in fusedLocation
    @SuppressLint("MissingPermission")
    private fun setupFusedLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                for (location in p0.locations) {
                    currentLocation.value = location
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
fun ScaffoldWithTopBar() {
    Scaffold(topBar = {
        TopAppBar(
            contentPadding = PaddingValues(horizontal = 16.dp),
            backgroundColor = MaterialTheme.colors.primary
        ) {
            Text(text = "Maps App For Location and Path", color = Color.White)
        }
    }) { pad ->
        val topPad = pad.calculateTopPadding()

        if (currentLocation.value != null && shouldShowMap.value) {
            MapScreen(
                newLocation = currentLocation.value!!,
                topPad
            )
        } else {
            AnimatedVisibility(
                modifier = Modifier.fillMaxSize(),
                visible = currentLocation.value == null,
                enter = fadeIn(),
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


@Composable
private fun MapScreen(
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
            modifier = Modifier
                .matchParentSize()
                .padding(top = topPad),
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

                // Does not work because ABOVE, check logcat if u want
                CoroutineScope(Dispatchers.IO).launch {
                    val data = downloadUrl(url)
                    withContext(Dispatchers.Main) {
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
                enter = fadeIn(),
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