package com.app.directionwithlocation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.app.directionwithlocation.ui.theme.DirectionWithLocationTheme
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DirectionWithLocationTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    MapScreen()
                }
            }
        }
    }
}

@Composable
fun MapScreen() {

    var showMarker by remember {
        mutableStateOf(false)
    }
    var destinationLocation by remember {
        mutableStateOf( LatLng(1.35, 103.87))}
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(destinationLocation, 14f)
    }
    var isMapLoaded by remember { mutableStateOf(false) }

    val uiSettings by remember { mutableStateOf(MapUiSettings()) }
    var properties by remember {
        mutableStateOf(MapProperties(mapType = MapType.NORMAL))
    }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.matchParentSize(),
            properties = properties,
            uiSettings = uiSettings,
            cameraPositionState = cameraPositionState,
            onMapLoaded = {
                isMapLoaded = true
            } ,
            onMapClick = {
                destinationLocation = it
                showMarker = true
            }
        ) {
            if(showMarker) {
                Marker(
                    state = MarkerState(position = destinationLocation),
                    title = "Destination",
                    snippet = "${destinationLocation.latitude}, ${destinationLocation.longitude}"
                )
            }
        }

        // Buttons
        Column(modifier = Modifier.fillMaxWidth().padding(all = 8.dp), horizontalAlignment = Alignment.End) {
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