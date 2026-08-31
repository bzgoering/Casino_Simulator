package com.casino.web.controller;

import com.casino.game.roulette.RouletteBet;
import com.casino.security.CasinoPrincipal;
import com.casino.security.CurrentUser;
import com.casino.service.BlackjackService;
import com.casino.service.RouletteService;
import com.casino.service.SlotsService;
import com.casino.web.dto.BlackjackResponse;
import com.casino.web.dto.GameRequests;
import com.casino.web.dto.RouletteResponse;
import com.casino.web.dto.SlotsResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The three games.
 *
 * <p>Every endpoint requires a token, guests included. Nothing about an outcome is taken from the
 * request: the client submits a stake and a decision, and the server decides what happens.
 */
@RestController
@RequestMapping("/api/games")
public class GameController {

    private final BlackjackService blackjack;
    private final SlotsService slots;
    private final RouletteService roulette;

    public GameController(BlackjackService blackjack, SlotsService slots, RouletteService roulette) {
        this.blackjack = blackjack;
        this.slots = slots;
        this.roulette = roulette;
    }

    /** Deals a new blackjack round and takes the opening bet on every box. */
    @PostMapping("/blackjack/deal")
    public BlackjackResponse deal(@Valid @RequestBody GameRequests.BlackjackDealRequest request) {
        CasinoPrincipal principal = CurrentUser.require();
        return BlackjackResponse.from(blackjack.deal(principal, request.bet(), request.handCount()));
    }

    /** Applies HIT, STAND, DOUBLE or SPLIT to the active hand. */
    @PostMapping("/blackjack/action")
    public BlackjackResponse action(@Valid @RequestBody GameRequests.BlackjackActionRequest request) {
        CasinoPrincipal principal = CurrentUser.require();
        return BlackjackResponse.from(blackjack.act(principal, request.roundId(), request.action()));
    }

    /** The hand currently in progress, for reconnecting after a refresh. */
    @GetMapping("/blackjack/current")
    public BlackjackResponse current() {
        return BlackjackResponse.from(blackjack.current(CurrentUser.require()));
    }

    @PostMapping("/slots/spin")
    public SlotsResponse spin(@Valid @RequestBody GameRequests.BetRequest request) {
        CasinoPrincipal principal = CurrentUser.require();
        return SlotsResponse.from(slots.spin(principal, request.bet()), request.bet());
    }

    /**
     * Spins the roulette wheel against every chip on the layout.
     *
     * <p>Each chip is rebuilt through {@link RouletteBet#of}, which rejects any selection that is
     * not placeable on a real cloth. Without that, a client could claim a two-number "split"
     * covering thirty numbers and collect 17:1 on it.
     */
    @PostMapping("/roulette/spin")
    public RouletteResponse rouletteSpin(@Valid @RequestBody GameRequests.RouletteSpinRequest request) {
        CasinoPrincipal principal = CurrentUser.require();
        List<RouletteBet> bets = request.bets().stream()
                .map(bet -> RouletteBet.of(bet.type(), bet.selection(), bet.amount()))
                .toList();
        return RouletteResponse.from(roulette.spin(principal, bets));
    }
}
