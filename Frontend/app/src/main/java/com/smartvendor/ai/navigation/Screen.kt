package com.smartvendor.ai.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash_screen")
    object Onboarding : Screen("onboarding_screen")
    object Login : Screen("login_screen")
    object Dashboard : Screen("dashboard_screen")
    object Scan : Screen("scan_screen/{billId}") {
        fun createRoute(billId: String) = "scan_screen/$billId"
    }
    object Billing : Screen("billing_screen/{billId}") {
        fun createRoute(billId: String) = "billing_screen/$billId"
    }
    object CheckoutSuccess : Screen("checkout_success_screen/{billId}") {
        fun createRoute(billId: String) = "checkout_success_screen/$billId"
    }
    object Inventory : Screen("inventory_screen")
    object ProductDetail : Screen("product_detail_screen/{productId}") {
        fun createRoute(productId: String) = "product_detail_screen/$productId"
    }
    object Reports : Screen("reports_screen")
    object History : Screen("history_screen")
    object Settings : Screen("settings_screen")
    object Resilience : Screen("resilience_screen")
}

