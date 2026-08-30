package dev.goose.sample.m2.catalog.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.airbnb.mvrx.compose.collectAsState
import dev.goose.mavericks.MavericksVmCreator
import dev.goose.mavericks.mavericksVmCreator
import dev.goose.mavericks.screenViewModel
import dev.goose.metro.gooseGraph
import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenEntry
import dev.goose.runtime.TypedScreenEntry
import dev.goose.runtime.sharedScreenElement
import dev.goose.sample.m2.catalog.api.CatalogScreen
import dev.goose.sample.m2.catalog.api.CatalogSharedKeys
import dev.goose.sample.m2.catalog.api.ItemDetailScreen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

internal fun itemColor(itemId: String): Color {
    val hues = listOf(0xFF7E57C2, 0xFF26A69A, 0xFFEF5350, 0xFF5C6BC0, 0xFFFFA726, 0xFF66BB6A)
    return Color(hues[itemId.hashCode().mod(hues.size)])
}

/** One-off composable injection: the price comes straight off the graph, no VM involved. */
@ContributesTo(AppScope::class)
interface PricingAccessor {
    val pricingService: PricingService
}

@ContributesIntoMap(AppScope::class)
@ClassKey(CatalogScreen::class)
@Inject
class CatalogEntry : ScreenEntry {
    @Composable
    override fun Content(screen: Screen, modifier: Modifier) {
        val viewModel = screenViewModel<CatalogViewModel, CatalogState>(screen)
        val state by viewModel.collectAsState()
        Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Catalog", style = MaterialTheme.typography.headlineMedium)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.items) { itemId ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.6f)
                            .sharedScreenElement(CatalogSharedKeys.itemSwatch(itemId))
                            .clip(RoundedCornerShape(16.dp))
                            .background(itemColor(itemId))
                            .clickable { viewModel.onItemClicked(itemId) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(itemId, color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@ContributesIntoMap(AppScope::class, binding = binding<ScreenEntry>())
@ClassKey(ItemDetailScreen::class)
@Inject
class ItemDetailEntry : TypedScreenEntry<ItemDetailScreen>() {
    @Composable
    override fun ScreenContent(screen: ItemDetailScreen, modifier: Modifier) {
        val viewModel = screenViewModel<ItemDetailViewModel, ItemDetailState>(screen)
        val state by viewModel.collectAsState()
        val pricingService = gooseGraph<PricingAccessor>().pricingService
        val price = remember(screen.itemId) { pricingService.priceOf(screen.itemId) }
        Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .sharedScreenElement(CatalogSharedKeys.itemSwatch(screen.itemId))
                    .clip(RoundedCornerShape(24.dp))
                    .background(itemColor(screen.itemId)),
                contentAlignment = Alignment.Center,
            ) {
                Text(screen.itemId, color = Color.White, style = MaterialTheme.typography.headlineLarge)
            }
            Text("Price: $price", style = MaterialTheme.typography.titleLarge)
            Button(onClick = viewModel::buyNow) { Text("Buy now") }
            state.lastPurchaseAddress?.let { Text("Shipped to: $it") }
        }
    }
}

@ContributesTo(AppScope::class)
interface CatalogModule {
    companion object {
        @Provides
        @IntoMap
        @ClassKey(CatalogViewModel::class)
        fun catalogVmCreator(factory: CatalogViewModel.Factory): MavericksVmCreator =
            mavericksVmCreator<CatalogState> { state, navigator -> factory.create(state, navigator) }

        @Provides
        @IntoMap
        @ClassKey(ItemDetailViewModel::class)
        fun itemDetailVmCreator(factory: ItemDetailViewModel.Factory): MavericksVmCreator =
            mavericksVmCreator<ItemDetailState> { state, navigator -> factory.create(state, navigator) }

        @Provides
        @IntoSet
        fun catalogSerializers(): SerializersModule = SerializersModule {
            polymorphic(NavKey::class) {
                subclass(CatalogScreen::class)
                subclass(ItemDetailScreen::class)
            }
        }
    }
}
