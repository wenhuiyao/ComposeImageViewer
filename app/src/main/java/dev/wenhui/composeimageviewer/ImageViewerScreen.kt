package dev.wenhui.composeimageviewer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import androidx.compose.ui.unit.dp
import dev.wenhui.library.ImageViewer
import dev.wenhui.library.Transform
import dev.wenhui.library.fillWidthOrHeight
import dev.wenhui.library.rememberImageState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageViewerScreen(modifier: Modifier = Modifier) {
    val pages = remember {
        listOf(
            R.drawable.space_1,
            R.drawable.android_wallpaper,
            R.drawable.space_2,
            0, // text content
            R.drawable.in_to_the_wood,
            R.drawable.curiosity_selfie,
            R.drawable.sunflower,
        )
    }
    val state = rememberPagerState { pages.size }
    HorizontalPager(
        state = state,
        modifier = modifier.fillMaxSize(),
        key = { pages[it] }
    ) { index ->
        ImageContentScreen(
            imageRes = pages[index],
            // HorizontalPager will intercept touch if page is scrolling,
            // don't enable image gesture until pager is settled
            enableGesture = !state.isScrollInProgress && index == state.currentPage,
        )
    }
}

@Composable
private fun ImageContentScreen(imageRes: Int, enableGesture: Boolean) {
    ImageViewer(
        modifier = Modifier.fillMaxSize(),
        enableGesture = enableGesture,
    ) {
        if (imageRes == 0) {
            var transform by remember { mutableStateOf<Transform?>(null) }
            val imageState = rememberImageState {
                this.transform = transform
            }
            // We can pan/zoom other content
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = loremIpsum(100),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .imageContentNode(imageState)
                        .background(color = MaterialTheme.colorScheme.secondaryContainer)
                        .padding(16.dp),

                )
                Button(
                    onClick = {
                        // Scale the content to the max, and move it to the bottom right corner,
                        // Or simply set transformOrigin to (1f,1f) will also acheive the same effect
                        transform = Transform(
                            translation = Offset(
                                x = -imageState.contentBounds.width * imageState.maxScale,
                                y = -imageState.contentBounds.height * imageState.maxScale,
                            ),
                            scale = imageState.maxScale,
                            shouldAnimate = true,
                        )
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 36.dp),
                ) {
                    Text(text = "Zoom to bottom right")
                }
            }
        } else {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .imageContentNode(rememberImageState())
                    .fillWidthOrHeight(),
            )
        }
    }
}

private val LOREM_IPSUM_SOURCE = """
Lorem ipsum dolor sit amet, consectetur adipiscing elit. Integer sodales
laoreet commodo. Phasellus a purus eu risus elementum consequat. Aenean eu
elit ut nunc convallis laoreet non ut libero. Suspendisse interdum placerat
risus vel ornare. Donec vehicula, turpis sed consectetur ullamcorper, ante
nunc egestas quam, ultricies adipiscing velit enim at nunc. Aenean id diam
neque. Praesent ut lacus sed justo viverra fermentum et ut sem. Fusce
convallis gravida lacinia. Integer semper dolor ut elit sagittis lacinia.
Praesent sodales scelerisque eros at rhoncus. Duis posuere sapien vel ipsum
ornare interdum at eu quam. Vestibulum vel massa erat. Aenean quis sagittis
purus. Phasellus arcu purus, rutrum id consectetur non, bibendum at nibh.
Duis nec erat dolor. Nulla vitae consectetur ligula. Quisque nec mi est. Ut
quam ante, rutrum at pellentesque gravida, pretium in dui. Cras eget sapien
velit. Suspendisse ut sem nec tellus vehicula eleifend sit amet quis velit.
Phasellus quis suscipit nisi. Nam elementum malesuada tincidunt. Curabitur
iaculis pretium eros, malesuada faucibus leo eleifend a. Curabitur congue
orci in neque euismod a blandit libero vehicula.
""".trim().replace('\n', ' ').split(" ")

/** Wrapper around [LoremIpsum] that supports adding an offset to get more variety. */
fun loremIpsum(words: Int = 20, offset: Int = 0): String {
    // Space-separation of words isn't a given in the real world, but this is a preview, so this
    // approach works well enough!
    return LOREM_IPSUM_SOURCE.subList(fromIndex = offset, toIndex = offset + words)
        .joinToString(separator = " ")
}
