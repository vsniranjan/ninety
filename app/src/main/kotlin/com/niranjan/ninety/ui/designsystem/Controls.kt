package com.niranjan.ninety.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.niranjan.ninety.R

/** Capitalize the rendered text while retaining the original label for accessibility. */
@Composable
internal fun CapsText(
    label: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
) {
    Text(label.uppercase(LocalLocale.current.platformLocale),
        modifier.semantics { text = AnnotatedString(label) },
        style = style, color = color, textAlign = textAlign)
}

@Composable
internal fun GhostButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = NinetyDimensions.touchTarget),
        enabled = enabled,
        shape = NinetyDimensions.pill,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface),
        border = BorderStroke(
            if (focused) 2.dp else NinetyDimensions.hairline,
            if (enabled) colors.onSurface else colors.outlineVariant,
        ),
        contentPadding = PaddingValues(horizontal = NinetyDimensions.content, vertical = NinetyDimensions.medium),
        interactionSource = interactions,
    ) { ProvideTextStyle(ActionTypography) { content() } }
}

@Composable
internal fun BackToTodayButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    GhostButton(onClick, modifier, enabled) {
        Icon(painterResource(R.drawable.ic_today), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(NinetyDimensions.small))
        Text(stringResource(R.string.back_to_today))
    }
}

@Composable
internal fun NinetyChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        shape = NinetyDimensions.pill,
        colors = FilterChipDefaults.filterChipColors(
            labelColor = colors.onSurface,
            selectedContainerColor = colors.primaryContainer,
            selectedLabelColor = colors.onPrimaryContainer,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = colors.outline,
            selectedBorderColor = colors.onSurface,
            selectedBorderWidth = NinetyDimensions.hairline,
        ),
    )
}

@Composable
internal fun NinetyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val surface = MaterialTheme.colorScheme.surface
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        label = label,
        singleLine = singleLine,
        isError = isError,
        supportingText = supportingText,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = NinetyDimensions.field,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = surface,
            unfocusedContainerColor = surface,
            disabledContainerColor = surface,
            errorContainerColor = surface,
        ),
    )
}

@Composable
internal fun NinetyAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        title = title,
        text = text,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    )
}

@Composable
internal fun NinetyMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = NinetyDimensions.panel,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(NinetyDimensions.hairline, MaterialTheme.colorScheme.outline),
        content = content,
    )
}

@Composable
internal fun NinetySnackbarHost(state: SnackbarHostState) {
    SnackbarHost(state) { data ->
        Snackbar(data, shape = NinetyDimensions.field)
    }
}
