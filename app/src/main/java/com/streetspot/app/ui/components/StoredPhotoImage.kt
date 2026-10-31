package com.streetspot.app.ui.components

import androidx.compose.material3.MaterialTheme
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import com.streetspot.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun StoredPhotoImage(
    relativePath: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val bitmapState = produceState<Bitmap?>(null, relativePath) {
        value = withContext(Dispatchers.IO) {
            val file = File(context.filesDir, relativePath)
            if (file.isFile && file.canonicalPath.startsWith(context.filesDir.canonicalPath + File.separator)) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else {
                null
            }
        }
    }
    val bitmap = bitmapState.value
    DisposableEffect(bitmap) {
        onDispose { bitmap?.recycle() }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.spotdex_photo_unavailable), color = Color.LightGray)
        }
    }
}
