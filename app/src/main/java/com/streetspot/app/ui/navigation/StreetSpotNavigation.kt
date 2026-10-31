package com.streetspot.app.ui.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.streetspot.app.R
import com.streetspot.app.data.social.FirebaseSocialRepository
import com.streetspot.app.data.social.SocialEntryPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import com.streetspot.app.ui.screens.PlaceholderScreen
import com.streetspot.app.ui.screens.home.HomeScreen
import com.streetspot.app.ui.screens.settings.SettingsScreen
import com.streetspot.app.ui.screens.social.CommunityScreen
import com.streetspot.app.ui.screens.social.SocialAuthMode
import com.streetspot.app.ui.screens.social.SocialAuthScreen
import com.streetspot.app.ui.screens.social.SocialEntryScreen
import kotlinx.coroutines.launch

private object StreetSpotRoutes {
    const val HOME = "home"
    const val SPOTDEX = "spotdex"
    const val BADGES = "badges"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val ADD_SPOT = "add_spot"
    const val SOCIAL_ENTRY = "social_entry"
    const val SOCIAL_AUTH = "social_auth/{mode}"
    const val COMMUNITY = "community"
    const val SPOT_DETAILS = "spot_details"
    const val SPOT_DETAILS_BY_ID = "spot_details/{modelId}"
    const val REWARD = "reward"
}

private data class TabDestination(
    val route: String,
    val labelResource: Int,
    val icon: ImageVector?,
)

private val tabDestinations = listOf(
    TabDestination(StreetSpotRoutes.HOME, R.string.nav_home, Icons.Filled.Home),
    TabDestination(StreetSpotRoutes.SPOTDEX, R.string.nav_spotdex, Icons.Filled.Search),
    TabDestination(StreetSpotRoutes.BADGES, R.string.nav_badges, Icons.Filled.Star),
    TabDestination(StreetSpotRoutes.STATS, R.string.nav_stats, null),
    TabDestination(StreetSpotRoutes.COMMUNITY, R.string.nav_community, Icons.Filled.Person),
)

private val mainRoutes = tabDestinations.map(TabDestination::route).toSet()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreetSpotNavigation(
    initialRoute: String?,
    externalNavigationRequests: Flow<String>?,
    startAtEntry: Boolean,
    entryPreferences: SocialEntryPreferences,
    socialRepository: FirebaseSocialRepository?,
) {
    val navController = rememberNavController()
    val requestedStartRoute = initialRoute?.takeIf {
        it == StreetSpotRoutes.HOME || it == StreetSpotRoutes.SPOTDEX
    } ?: StreetSpotRoutes.HOME
    val startRoute = if (startAtEntry) StreetSpotRoutes.SOCIAL_ENTRY else requestedStartRoute
    var tabRootRoute by remember(startRoute) {
        mutableStateOf(if (startAtEntry) StreetSpotRoutes.HOME else requestedStartRoute)
    }
    val navigationScope = rememberCoroutineScope()

    LaunchedEffect(externalNavigationRequests, navController, startAtEntry) {
        externalNavigationRequests?.collect { route ->
            if (route == StreetSpotRoutes.HOME || route == StreetSpotRoutes.SPOTDEX) {
                if (startAtEntry) {
                    runCatching { entryPreferences.markEntryChoiceMade() }
                    tabRootRoute = StreetSpotRoutes.HOME
                    navController.navigate(route) {
                        popUpTo(StreetSpotRoutes.SOCIAL_ENTRY) { inclusive = true }
                        launchSingleTop = true
                    }
                } else {
                    navController.navigate(route) {
                        launchSingleTop = true
                    }
                }
            }
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: StreetSpotRoutes.HOME
    val isMainRoute = currentRoute in mainRoutes
    val canNavigateBack = !isMainRoute && currentRoute != StreetSpotRoutes.SOCIAL_ENTRY

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(currentTitleResource(currentRoute))) },
                navigationIcon = {
                    if (canNavigateBack) {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                actions = {
                    if (currentRoute == StreetSpotRoutes.HOME) {
                        IconButton(
                            onClick = { navController.navigateToRoute(StreetSpotRoutes.SETTINGS) },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.action_open_settings),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (isMainRoute) {
                StreetSpotNavigationBar(
                    currentRoute = currentRoute,
                    onSelectRoute = { route -> navController.navigateToTab(route, tabRootRoute) },
                    onNewSpot = { navController.navigateToRoute(StreetSpotRoutes.ADD_SPOT) },
                )
            }
        },
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            composable(StreetSpotRoutes.HOME) {
                HomeScreen(
                    onAddSpot = { navController.navigateToRoute(StreetSpotRoutes.ADD_SPOT) },
                    onOpenModel = { modelId -> navController.navigateToRoute("spot_details/$modelId") },
                    onOpenAchievements = { navController.navigateToTab(StreetSpotRoutes.BADGES, tabRootRoute) },
                )
            }
            composable(StreetSpotRoutes.SPOTDEX) {
                com.streetspot.app.ui.screens.spotdex.SpotdexScreen(
                    onOpenModel = { modelId ->
                        navController.navigateToRoute("spot_details/$modelId")
                    },
                    onAddSpot = { navController.navigateToRoute(StreetSpotRoutes.ADD_SPOT) },
                )
            }
            composable(StreetSpotRoutes.BADGES) {
                com.streetspot.app.ui.screens.achievements.AchievementsScreen()
            }
            composable(StreetSpotRoutes.STATS) {
                com.streetspot.app.ui.screens.stats.StatisticsScreen()
            }
            composable(StreetSpotRoutes.SETTINGS) {
                SettingsScreen()
            }
            composable(StreetSpotRoutes.ADD_SPOT) {
                com.streetspot.app.ui.screens.addspot.AddSpotScreen(
                    onFinish = { navController.navigateToTab(StreetSpotRoutes.SPOTDEX, tabRootRoute) },
                    onOpenExisting = { modelId ->
                        navController.navigateToRoute("spot_details/$modelId")
                    },
                )
            }
            composable(StreetSpotRoutes.SPOT_DETAILS) {
                PlaceholderScreen(
                    headline = stringResource(R.string.spot_details_placeholder_headline),
                    description = stringResource(R.string.spot_details_placeholder_description),
                )
            }
            composable(
                route = StreetSpotRoutes.SPOT_DETAILS_BY_ID,
                arguments = listOf(navArgument("modelId") { type = NavType.StringType }),
            ) {
                com.streetspot.app.ui.screens.spotdetails.SpotDetailsScreen(
                    modelId = it.arguments?.getString("modelId").orEmpty(),
                    onBackToCollection = { navController.navigateToTab(StreetSpotRoutes.SPOTDEX, tabRootRoute) },
                )
            }
            composable(StreetSpotRoutes.REWARD) {
                PlaceholderScreen(
                    headline = stringResource(R.string.reward_placeholder_headline),
                    description = stringResource(R.string.reward_placeholder_description),
                )
            }
            composable(StreetSpotRoutes.SOCIAL_ENTRY) {
                SocialEntryScreen(
                    onCreateAccount = { navController.navigateToRoute(socialAuthRoute(SocialAuthMode.CREATE)) },
                    onSignIn = { navController.navigateToRoute(socialAuthRoute(SocialAuthMode.SIGN_IN)) },
                    onContinueOffline = {
                        navigationScope.launch {
                            entryPreferences.markEntryChoiceMade()
                            tabRootRoute = StreetSpotRoutes.HOME
                            navController.navigateToHome(StreetSpotRoutes.SOCIAL_ENTRY)
                        }
                    },
                )
            }
            composable(
                route = StreetSpotRoutes.SOCIAL_AUTH,
                arguments = listOf(navArgument("mode") { type = NavType.StringType }),
            ) { entry ->
                val mode = entry.arguments?.getString("mode")
                    ?.let { runCatching { SocialAuthMode.valueOf(it) }.getOrNull() }
                    ?: SocialAuthMode.SIGN_IN
                SocialAuthScreen(
                    repository = socialRepository,
                    initialMode = mode,
                    onAuthenticated = {
                        navigationScope.launch {
                            entryPreferences.markEntryChoiceMade()
                            tabRootRoute = StreetSpotRoutes.HOME
                            navController.navigateToHome(startRoute)
                        }
                    },
                    onBack = { navController.navigateUp() },
                )
            }
            composable(StreetSpotRoutes.COMMUNITY) {
                CommunityScreen(
                    onOpenAuth = { mode -> navController.navigateToRoute(socialAuthRoute(mode)) },
                )
            }
        }
    }
}

@Composable
private fun StreetSpotNavigationBar(
    currentRoute: String,
    onSelectRoute: (String) -> Unit,
    onNewSpot: () -> Unit,
) {
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        tabDestinations.take(2).forEach { destination ->
            NavigationDestinationItem(
                destination = destination,
                selected = currentRoute == destination.route,
                colors = itemColors,
                onClick = { onSelectRoute(destination.route) },
            )
        }

        NavigationBarItem(
            selected = false,
            onClick = onNewSpot,
            icon = {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.nav_new_spot),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            },
            colors = itemColors,
        )

        tabDestinations.drop(2).forEach { destination ->
            NavigationDestinationItem(
                destination = destination,
                selected = currentRoute == destination.route,
                colors = itemColors,
                onClick = { onSelectRoute(destination.route) },
            )
        }
    }
}

@Composable
private fun RowScope.NavigationDestinationItem(
    destination: TabDestination,
    selected: Boolean,
    colors: androidx.compose.material3.NavigationBarItemColors,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            if (destination.icon != null) {
                Icon(imageVector = destination.icon, contentDescription = null)
            } else {
                StatsGlyph(tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        label = { Text(stringResource(destination.labelResource)) },
        colors = colors,
    )
}

@Composable
private fun StatsGlyph(tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val baseline = size.height * 0.84f
        val barWidth = size.width * 0.16f
        val strokeWidth = 1.8.dp.toPx()
        val barHeights = listOf(0.36f, 0.58f, 0.78f)

        barHeights.forEachIndexed { index, heightFraction ->
            val left = size.width * (0.18f + index * 0.25f)
            val top = baseline - size.height * heightFraction
            drawRoundRect(
                color = tint,
                topLeft = Offset(left, top),
                size = Size(barWidth, baseline - top),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }

        drawLine(
            color = tint,
            start = Offset(size.width * 0.12f, baseline),
            end = Offset(size.width * 0.88f, baseline),
            strokeWidth = strokeWidth,
        )
    }
}

private fun currentTitleResource(route: String): Int = when (route) {
    StreetSpotRoutes.HOME -> R.string.screen_title_home
    StreetSpotRoutes.SPOTDEX -> R.string.screen_title_spotdex
    StreetSpotRoutes.BADGES -> R.string.screen_title_badges
    StreetSpotRoutes.STATS -> R.string.screen_title_stats
    StreetSpotRoutes.SETTINGS -> R.string.screen_title_settings
    StreetSpotRoutes.ADD_SPOT -> R.string.screen_title_add_spot
    StreetSpotRoutes.SPOT_DETAILS, StreetSpotRoutes.SPOT_DETAILS_BY_ID -> R.string.screen_title_spot_details
    StreetSpotRoutes.REWARD -> R.string.screen_title_reward
    StreetSpotRoutes.COMMUNITY -> R.string.screen_title_community
    StreetSpotRoutes.SOCIAL_AUTH -> R.string.screen_title_social_auth
    else -> R.string.app_name
}

private fun NavHostController.navigateToRoute(route: String) {
    navigate(route) {
        launchSingleTop = true
    }
}

private fun NavHostController.navigateToTab(route: String, rootRoute: String) {
    navigate(route) {
        popUpTo(rootRoute) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.navigateToHome(rootRoute: String) {
    navigate(StreetSpotRoutes.HOME) {
        popUpTo(rootRoute) { inclusive = true }
        launchSingleTop = true
    }
}

private fun socialAuthRoute(mode: SocialAuthMode): String = "social_auth/${mode.name}"
