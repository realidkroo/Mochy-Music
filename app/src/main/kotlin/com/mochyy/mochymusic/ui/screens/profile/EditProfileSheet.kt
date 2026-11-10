// EditProfileSheet.kt
//
// "Edit profile": the username and picture the profile already has, ready to change. Built from the
// onboarding's own pieces, so it reads like the profile step you filled in on day one.

package com.mochyy.mochymusic.ui.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.screens.setup.PhotoCropperContainer
import com.mochyy.mochymusic.ui.screens.setup.onboarding.Onb
import com.mochyy.mochymusic.ui.screens.setup.onboarding.OnbButton
import com.mochyy.mochymusic.ui.screens.setup.onboarding.OnbTextField
import com.mochyy.mochymusic.ui.screens.setup.onboarding.OnboardingPage
import com.mochyy.mochymusic.ui.screens.setup.onboarding.PageBadge
import kotlinx.coroutines.launch
import com.mochyy.mochymusic.ui.component.RemoteFocus

/** Opens the editor filled in with the name and picture the profile shows now. */
fun PopupSheetState.showEditProfileSheet(currentName: String, currentPhoto: Any?) {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle(fullBleed = true, surface = Onb.Page) {
        EditProfileContent(currentName, currentPhoto, onSaved = { sheet?.dismiss() })
    }
}

@Composable
private fun EditProfileContent(currentName: String, currentPhoto: Any?, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(currentName) }
    // a picked picture goes through the cropper first; only the cropped copy is kept
    var uncropped by remember { mutableStateOf<Uri?>(null) }
    var cropped by remember { mutableStateOf<Uri?>(null) }
    var saving by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        if (picked != null) uncropped = picked
    }
    fun pickPhoto() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    PhotoCropperContainer(
        uri = uncropped,
        onDone = {
            cropped = it
            uncropped = null
        },
        onCancel = { uncropped = null },
        onSelectNewImage = ::pickPhoto,
    ) {
        OnboardingPage(
            badge = { PageBadge(painterResource(R.drawable.person)) },
            title = stringResource(R.string.profile_edit_title),
            contentTop = 22.dp,
            modifier = Modifier.background(Onb.Page),
            footer = {
                Text(
                    stringResource(R.string.profile_data_privacy_hint),
                    color = Onb.Faint,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp),
                )
                OnbButton(
                    text = stringResource(R.string.profile_done),
                    enabled = name.isNotBlank() && !saving,
                    onClick = {
                        saving = true
                        scope.launch {
                            ProfileStore.save(context, name.trim(), cropped)
                            onSaved()
                        }
                    },
                )
            },
        ) {
            Text(stringResource(R.string.profile_username_label), color = Onb.Muted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            OnbTextField(value = name, onValueChange = { name = it.take(32) }, placeholder = stringResource(R.string.profile_username_placeholder))
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.profile_picture_label), color = Onb.Muted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            EditablePhoto(photo = cropped ?: currentPhoto, onClick = ::pickPhoto)
        }
    }
}

/** The picture as it will be saved. Tapping it picks a new one; it dips a little while pressed. */
@Composable
private fun EditablePhoto(photo: Any?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(100.dp)
            .graphicsLayer {
                val s = if (pressed) 0.95f else 1f
                scaleX = s
                scaleY = s
            }
            .clip(CircleShape)
            .background(Color(0xFF4F4F4F))
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick),
    ) {
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = stringResource(R.string.profile_picture_cd),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painterResource(R.drawable.add),
                contentDescription = stringResource(R.string.profile_picture_add_cd),
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(34.dp),
            )
        }
    }
}
