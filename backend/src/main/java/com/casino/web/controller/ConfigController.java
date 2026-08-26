package com.casino.web.controller;

import com.casino.game.blackjack.BlackjackRules;
import com.casino.game.roulette.RouletteBetType;
import com.casino.game.roulette.RouletteWheel;
import com.casino.game.slots.SlotPaytable;
import com.casino.game.slots.SlotSymbol;
import com.casino.service.BetValidator;
import com.casino.service.BlackjackService;
import com.casino.web.dto.ConfigResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public table information for the lobby: limits, paytables, wheel layout.
 *
 * <p>The advertised figures are derived from the same code that runs the games, never typed in
 * separately. The slot RTP is computed by enumerating the full outcome space on request, so the
 * number on the paytable screen cannot drift away from what the machine actually pays.
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final BetValidator betValidator;
    private final BlackjackService blackjack;

    public ConfigController(BetValidator betValidator, BlackjackService blackjack) {
        this.betValidator = betValidator;
        this.blackjack = blackjack;
    }

    @GetMapping
    public ConfigResponse config() {
        BlackjackRules rules = blackjack.rules();

        var blackjackInfo = new ConfigResponse.BlackjackInfo(
                rules.deckCount(),
                rules.dealerHitsSoft17(),
                rules.blackjackPayout() == 1.5 ? "3:2" : rules.blackjackPayout() + "x",
                rules.maxSplits(),
                rules.doubleAfterSplit());

        var slotsInfo = new ConfigResponse.SlotsInfo(
                SlotPaytable.REEL_STRIP.stream().map(Enum::name).toList(),
                paytableSummary(),
                computeSlotRtp());

        Map<String, Integer> roulettePayouts = new LinkedHashMap<>();
        for (RouletteBetType type : RouletteBetType.values()) {
            roulettePayouts.put(type.name(), type.payoutToOne());
        }
        var rouletteInfo = new ConfigResponse.RouletteInfo(
                RouletteWheel.POCKET_ORDER,
                roulettePayouts,
                100.0 / RouletteWheel.POCKET_COUNT);

        return new ConfigResponse(
                betValidator.minBet(),
                betValidator.maxBet(),
                betValidator.maxRouletteBets(),
                blackjackInfo,
                slotsInfo,
                rouletteInfo);
    }

    /** Best payout per named combination, for the paytable screen. */
    private static Map<String, Integer> paytableSummary() {
        Map<String, Integer> summary = new TreeMap<>();
        List<SlotSymbol> strip = SlotPaytable.REEL_STRIP;
        for (SlotSymbol a : strip) {
            for (SlotSymbol b : strip) {
                for (SlotSymbol c : strip) {
                    int multiplier = SlotPaytable.multiplierFor(a, b, c);
                    if (multiplier > 0) {
                        summary.merge(SlotPaytable.describe(a, b, c), multiplier, Math::max);
                    }
                }
            }
        }
        return summary;
    }

    /** Exact RTP as a percentage, by enumerating all 32^3 equally likely outcomes. */
    private static double computeSlotRtp() {
        List<SlotSymbol> strip = SlotPaytable.REEL_STRIP;
        long returned = 0;
        for (SlotSymbol a : strip) {
            for (SlotSymbol b : strip) {
                for (SlotSymbol c : strip) {
                    returned += SlotPaytable.multiplierFor(a, b, c);
                }
            }
        }
        long outcomes = (long) strip.size() * strip.size() * strip.size();
        return Math.round(returned * 1_000_000.0 / outcomes) / 10_000.0;
    }
}
