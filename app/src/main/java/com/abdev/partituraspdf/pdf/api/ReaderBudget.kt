package com.abdev.partituraspdf.pdf.api

import com.abdev.partituraspdf.core.foundation.ReaderResourceBudget
import com.abdev.partituraspdf.core.result.*

/** G consumes both the existing native ceiling and current heap budget; zero headroom means no allocation. */
fun boundedRenderLimits(budget: ReaderResourceBudget, ceiling: RenderLimits): Outcome<RenderLimits> =
    if (budget.maxArgbPixels < 1) Outcome.Failure(AppError.Resource.RENDER_LIMIT)
    else Outcome.Success(RenderLimits(ceiling.maxDimension, minOf(ceiling.maxPixels, budget.maxArgbPixels)))
