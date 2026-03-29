package com.akatsuki.trading.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.akatsuki.trading.data.model.AuthState
import com.akatsuki.trading.ui.screens.ConnectScreen
import com.akatsuki.trading.ui.screens.CredentialsScreen
import com.akatsuki.trading.ui.screens.MainHostScreen
import com.akatsuki.trading.viewmodel.AuthViewModel

object Routes {
    const val CREDENTIALS = "credentials"
    const val CONNECT = "connect"
    const val MAIN = "main"
}

@Composable
fun AkatsukiNavGraph(modifier: Modifier = Modifier) {
    val navController: NavHostController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.authState.collectAsState()

    LaunchedEffect(authState) {
        val currentRoute = navController.currentDestination?.route
        when (authState) {
            is AuthState.NeedsCredentials -> {
                if (currentRoute != Routes.CREDENTIALS) {
                    navController.navigate(Routes.CREDENTIALS) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            is AuthState.NeedsConnect -> {
                if (currentRoute != Routes.CONNECT) {
                    navController.navigate(Routes.CONNECT) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            is AuthState.Connected -> {
                if (currentRoute != Routes.MAIN) {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            else -> {}
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.CONNECT,
        modifier = modifier
    ) {
        composable(Routes.CREDENTIALS) {
            CredentialsScreen(
                authViewModel = authViewModel,
                onCredentialsSaved = {
                    navController.navigate(Routes.CONNECT) {
                        popUpTo(Routes.CREDENTIALS) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.CONNECT) {
            ConnectScreen(
                authViewModel = authViewModel,
                onConnected = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.CONNECT) { inclusive = true }
                    }
                },
                onEditCredentials = {
                    navController.navigate(Routes.CREDENTIALS) {
                        popUpTo(Routes.CONNECT) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.MAIN) {
            MainHostScreen(
                authViewModel = authViewModel,
                onLogout = {
                    navController.navigate(Routes.CONNECT) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                }
            )
        }
    }
}
