package tse.test.multiplatform

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

import <Unresolved reference>testmultiplatform</Unresolved reference>.<Unresolved reference>composeapp</Unresolved reference>.<Unresolved reference>generated</Unresolved reference>.<Unresolved reference>resources</Unresolved reference>.<Unresolved reference>Res</Unresolved reference>
import <Unresolved reference>testmultiplatform</Unresolved reference>.<Unresolved reference>composeapp</Unresolved reference>.<Unresolved reference>generated</Unresolved reference>.<Unresolved reference>resources</Unresolved reference>.<Unresolved reference>compose_multiplatform</Unresolved reference>

@Composable
@Preview
fun App() {
    MaterialTheme {
        var showContent by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = { showContent = !showContent }) {
                Text("Click me!")
            }
            AnimatedVisibility(showContent) {
                val greeting = remember { Greeting().greet() }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(<Expected: DrawableResource, got: compose_multiplatform>Res.drawable.compose_multiplatform</Expected: DrawableResource, got: compose_multiplatform><Unresolved reference>Res</Unresolved reference>.<Unresolved reference>drawable</Unresolved reference>.<Unresolved reference>compose_multiplatform</Unresolved reference>), null)
                    Text("Compose: $greeting")
                }
            }
        }
    }
}
