package com.aimafia.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks token usage across all API calls for cost estimation.
 * Thread-safe singleton.
 */
public final class TokenTracker {
    private static final Logger logger = LoggerFactory.getLogger(TokenTracker.class);

    private static volatile TokenTracker instance;

    // Approximate costs per 1000 tokens (in USD cents)
    // These should be updated based on actual model pricing
    private static final double INPUT_COST_PER_1K = 0.5; // $0.005 per 1K input tokens
    private static final double OUTPUT_COST_PER_1K = 1.5; // $0.015 per 1K output tokens

    private final AtomicLong totalInputTokens;
    private final AtomicLong totalOutputTokens;
    private final AtomicInteger requestCount;

    private TokenTracker() {
        this.totalInputTokens = new AtomicLong(0);
        this.totalOutputTokens = new AtomicLong(0);
        this.requestCount = new AtomicInteger(0);
    }

    /**
     * Gets the singleton instance.
     *
     * @return The TokenTracker instance
     */
    public static TokenTracker getInstance() {
        if (instance == null) {
            synchronized (TokenTracker.class) {
                if (instance == null) {
                    instance = new TokenTracker();
                }
            }
        }
        return instance;
    }

    /**
     * Resets the tracker (for testing or new games).
     */
    public static synchronized void reset() {
        instance = null;
    }

    /**
     * Adds token usage from a single request.
     *
     * @param inputTokens  Number of input tokens
     * @param outputTokens Number of output tokens
     */
    public void addUsage(int inputTokens, int outputTokens) {
        totalInputTokens.addAndGet(inputTokens);
        totalOutputTokens.addAndGet(outputTokens);
        requestCount.incrementAndGet();

        logger.debug("Token usage: input={}, output={}, total requests={}",
                inputTokens, outputTokens, requestCount.get());
    }

    /**
     * Gets total input tokens used.
     *
     * @return Total input tokens
     */
    public long getTotalInputTokens() {
        return totalInputTokens.get();
    }

    /**
     * Gets total output tokens used.
     *
     * @return Total output tokens
     */
    public long getTotalOutputTokens() {
        return totalOutputTokens.get();
    }

    /**
     * Gets total tokens used (input + output).
     *
     * @return Total tokens
     */
    public long getTotalTokens() {
        return totalInputTokens.get() + totalOutputTokens.get();
    }

    /**
     * Gets the number of API requests made.
     *
     * @return Request count
     */
    public int getRequestCount() {
        return requestCount.get();
    }

    /**
     * Estimates the cost in USD cents.
     *
     * @return Estimated cost in cents
     */
    public double getEstimatedCostCents() {
        double inputCost = (totalInputTokens.get() / 1000.0) * INPUT_COST_PER_1K;
        double outputCost = (totalOutputTokens.get() / 1000.0) * OUTPUT_COST_PER_1K;
        return inputCost + outputCost;
    }

    /**
     * Estimates the cost in USD.
     *
     * @return Estimated cost in dollars
     */
    public double getEstimatedCostUSD() {
        return getEstimatedCostCents() / 100.0;
    }

    /**
     * Gets a formatted summary of token usage.
     *
     * @return Summary string
     */
    public String getSummary() {
        return String.format("""
                === Token Usage Summary ===
                Requests:      %d
                Input tokens:  %,d
                Output tokens: %,d
                Total tokens:  %,d
                Estimated cost: $%.4f USD
                """,
                getRequestCount(),
                getTotalInputTokens(),
                getTotalOutputTokens(),
                getTotalTokens(),
                getEstimatedCostUSD());
    }

    /**
     * Logs the current usage summary.
     */
    public void logSummary() {
        logger.info("\n{}", getSummary());
    }

    @Override
    public String toString() {
        return String.format("TokenTracker{requests=%d, totalTokens=%d, cost=$%.4f}",
                getRequestCount(), getTotalTokens(), getEstimatedCostUSD());
    }
}
