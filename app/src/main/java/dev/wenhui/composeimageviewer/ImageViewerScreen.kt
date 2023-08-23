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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import androidx.compose.ui.unit.dp
import dev.wenhui.library.ImageViewer
import dev.wenhui.library.fillWidthOrHeight
import dev.wenhui.library.rememberImageState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageViewerScreen(modifier: Modifier = Modifier) {
    val loadedMap = remember { mutableStateMapOf<Int, Boolean>() }
    val pages = remember {
        listOf(
            R.drawable.space_1,
            R.drawable.space_2,
            R.drawable.taipei_101,
            0, // This is text content
            R.drawable.in_to_the_wood,
            R.drawable.sunflower,
        )
    }
    val state = rememberPagerState { pages.size }
    HorizontalPager(
        state = state,
        modifier = modifier
    ) { index ->
        if (loadedMap[index] == true) {
            ImageContentScreen(
                imageRes = pages[index],
                // HorizontalPager will intercept touch if page is scrolling,
                // don't enable image gesture until pager is settled
                enableGesture = !state.isScrollInProgress && index == state.currentPage
            )
        } else {
            // This is to simulate loading image asynchronously
            ImageLoadingScreen { loadedMap[state.currentPage] = true }
        }
    }
}

@Composable
private fun ImageContentScreen(imageRes: Int, enableGesture: Boolean) {
    ImageViewer(
        modifier = Modifier.fillMaxSize(),
        enableGesture = enableGesture
    ) {
        if (imageRes == 0) {
            // We can pan/zoom other content
            Text(
                text = loremIpsum(100),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .imageContentNode(rememberImageState())
                    .background(color = MaterialTheme.colorScheme.secondaryContainer)
                    .padding(16.dp)

            )
        } else {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .imageContentNode(rememberImageState())
                    .fillWidthOrHeight()
            )
        }
    }
}

@Composable
private fun ImageLoadingScreen(onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        Button(
            onClick = onClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
        ) {
            Text(text = "Tap to load image")
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

