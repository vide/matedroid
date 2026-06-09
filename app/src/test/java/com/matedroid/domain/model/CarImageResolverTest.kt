package com.matedroid.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CarImageResolverTest {

    @Test
    fun `DiamondBlack maps to PX02 color code`() {
        // TeslamateAPI reports the Juniper/Highland black as "DiamondBlack" (one word, no space).
        // Regression: this used to be unmapped, falling back to white (PPSW).
        assertEquals("PX02", CarImageResolver.mapColor("DiamondBlack"))
    }

    @Test
    fun `reversed and spaced spellings of Diamond Black also map to PX02`() {
        assertEquals("PX02", CarImageResolver.mapColor("Diamond Black"))
        assertEquals("PX02", CarImageResolver.mapColor("BlackDiamond"))
    }

    @Test
    fun `black Model Y Juniper Premium resolves to the black asset, not white`() {
        // Real user config: Model Y, DiamondBlack, 19" Crossflow, trim "74" (Long Range/Premium).
        val path = CarImageResolver.getAssetPath(
            model = "Y",
            exteriorColor = "DiamondBlack",
            wheelType = "Crossflow19",
            trimBadging = "74",
        )
        assertEquals("car_images/myj_PX02_WY19P.png", path)
    }
}
