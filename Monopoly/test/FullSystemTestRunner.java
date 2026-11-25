package test;

import engine.GameEngine;
import engine.GameEngine.ActionResult;
import engine.GameEngine.EngineEvent;
import engine.bot.BotController;
import model.Board;
import model.Dice;
import model.GameRoom;
import model.Player;
import model.PlayerStatus;
import model.RoomStatus;
import model.event.ContinentalDriftEvent;
import model.event.EarthquakeEvent;
import model.event.FrozenTileEvent;
import model.event.GameEventManager;
import model.event.PropertyBoomEvent;
import model.event.RichTaxEvent;
import model.event.StimulusDayEvent;
import model.item.ArrestWarrantItem;
import model.item.DynamiteItem;
import model.item.FortuneSwapItem;
import model.item.HostileTakeoverItem;
import model.item.Inventory;
import model.item.Item;
import model.item.ItemTargetContext;
import model.item.MagicDiceItem;
import model.item.RentShieldItem;
import model.tile.FreeParkingTile;
import model.tile.GoTile;
import model.tile.GoToJailTile;
import model.tile.JailTile;
import model.tile.PropertyTile;
import model.tile.RailroadProperty;
import model.tile.StreetProperty;
import model.tile.TaxTile;
import model.tile.Tile;
import model.tile.UtilityProperty;
import server.controller.AuthRestController;
import server.controller.GameWebSocketController;
import server.controller.RoomRestController;
import server.gateway.ApiGatewayServer;
import server.gateway.GatewayConfig;
import server.persistence.UserRepository;
import server.security.CloudflareOriginFilter;
import server.security.JwtTokenProvider;
import server.security.PasswordEncoderUtil;
import server.service.RoomManagerService;
import server.service.TurnTimerService;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Pure-JDK Full System Verification Runner (Suites A - D).
 * Zero external JAR dependencies.
 */
public class FullSystemTestRunner {

    private static final String TEST_JWT_SECRET = "TestSuiteJwtSecretKey2026PtitOopProjectLongEnough";
    private static final String PROD_ORIGIN_SECRET = "test-prod-secret-key";

    private final List<String> failures = new ArrayList<>();
    private final Map<String, int[]> suiteCounts = new LinkedHashMap<>();

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        FullSystemTestRunner runner = new FullSystemTestRunner();
        runner.runAll();
    }

    private void runAll() {
        System.out.println("==================================================================");
        System.out.println("  MONOPOLY FULL SYSTEM TEST RUNNER (PURE JDK - SUITES A..E)");
        System.out.println("==================================================================");

        // Suite A — Core Domain OOP & Engine
        runTest("Suite A", "Player encapsulation, money, jail, bail & bankruptcy",
                this::testPlayerEncapsulationMoneyJailBankruptcy);
        runTest("Suite A", "Board 40 tiles & Tile.onLand polymorphism",
                this::testBoardAndTilePolymorphism);
        runTest("Suite A", "StreetProperty, RailroadProperty, UtilityProperty & PropertyTile rent",
                this::testPropertyRentAndModifiers);
        runTest("Suite A", "Inventory capacity/immutability & all 6 Item effects",
                this::testInventoryAndItemEffects);
        runTest("Suite A", "GameEventManager instant & timed events",
                this::testGameEventManager);
        runTest("Suite A", "GameEngine authoritative validation & anti-cheat",
                this::testGameEngineValidationAndAntiCheat);
        runTest("Suite A", "GameEngine multi-thread concurrency safety",
                this::testGameEngineConcurrency);
        runTest("Suite A", "BotController & TurnTimerService timeout/takeover",
                this::testBotControllerAndTurnTimerService);

        // Suite B — Cloudflare Dev/Prod Origin Protection via Live API Gateway
        runTest("Suite B", "DEV mode bypasses X-Origin-Verify for HTTP and WebSocket /ws",
                this::testCloudflareDevModeOriginBypass);
        runTest("Suite B", "PROD mode enforces X-Origin-Verify for HTTP and WebSocket /ws",
                this::testCloudflareProdModeOriginShield);

        // Suite C — Full REST API & Security Hardening over Real TCP HTTP
        runTest("Suite C", "HTTP health, CORS OPTIONS, 405 method whitelist, 413 >100KB, 404 unknown path",
                this::testRestSecurityHardening);
        runTest("Suite C", "Auth register/login/me lifecycle, UTF-8 Vietnamese, duplicate/weak/unauthorized",
                this::testRestAuthLifecycle);
        runTest("Suite C", "Room create/list/snapshot/join/non-host start 403/host start 200/leave",
                this::testRestRoomLifecycle);

        // Suite D — Real WebSocket (RFC 6455) + STOMP 1.2 Multiplayer & Anti-Cheat
        runTest("Suite D", "Unauthorized STOMP CONNECT rejected with STOMP ERROR",
                this::testStompUnauthorizedConnect);
        runTest("Suite D", "Real WS + STOMP 1.2 multiplayer, anti-cheat, roll, buy & end-turn sync",
                this::testStompMultiplayerAndAntiCheat);
        runTest("Suite D", "Snapshot timer/logs sync, ROOM_JOINED/ROOM_LEFT & Timeout/Bot broadcasts",
                this::testStompRoomLeaveTimeoutAndBotBroadcasts);

        // Suite E — Regression Suite (GO/Bankruptcy, Jail, Bot Concurrency, Upgrade, Leave, WS Heartbeat/Reconnect/Auth, Prod JWT)
        runTest("Suite E", "Direct GO landing (35+5->0) awards $200 & negative steps award $0",
                this::testSuiteE_GoLandingAndNegativeSteps);
        runTest("Suite E", "declareBankruptcy(null) resets houses/hotel/mortgaged & getOwnedProperties unmodifiable",
                this::testSuiteE_BankruptcyNullResetAndUnmodifiableOwnedProps);
        runTest("Suite E", "Jail roll/skip 3 turns auto-bail/bankruptcy, Bot in Jail & payBail reclaims TIMEOUT",
                this::testSuiteE_JailMechanicsAndPayBailReclaim);
        runTest("Suite E", "Concurrent BotController.processBotTurn + human actions/snapshot zero deadlock",
                this::testSuiteE_ConcurrentBotAndHumanNoDeadlock);
        runTest("Suite E", "StreetProperty upgrade via STOMP /upgrade-property -> PROPERTY_UPGRADED + houseCount",
                this::testSuiteE_StompUpgradeProperty);
        runTest("Suite E", "Mid-game POST /leave surrenders leaver, clears properties & finishes 2-player match",
                this::testSuiteE_MidGameLeaveSurrenderAndFinish);
        runTest("Suite E", "STOMP heartbeat negotiation, WS reconnect snapshot, non-member subscribe ERROR & PROD JWT_SECRET",
                this::testSuiteE_StompHeartbeatReconnectAuthAndProdJwt);
        runTest("Suite E", "Timeout during AWAITING_DECISION (doubles/unowned), actionPhase & ensureBotTurnResolved",
                this::testSuiteE_TimeoutInAwaitingDecisionAndEnsureBotResolved);
        runTest("Suite E", "Dedicated player.isBot() auto-plays without 30s wait or BOT_ALREADY_ACTIVE",
                this::testSuiteE_DedicatedBotAutoPlaysWithoutWaiting30s);
        runTest("Suite E", "Timeline monotonic id/ts, TIMELINE_APPENDED, Asia/Ho_Chi_Minh logs, rentTable & Bot newPosition",
                this::testSuiteE_TimelineRentTableAndBotPositionSync);
        runTest("Suite E", "Auto RENT_SHIELD -> ITEM_USED auto=true STOMP broadcast + exactly one log line",
                this::testSuiteE_AutoRentShieldStompAndSingleLog);

        printSummaryAndExit();
    }

    private void runTest(String suite, String name, ThrowingRunnable test) {
        int[] counts = suiteCounts.computeIfAbsent(suite, k -> new int[2]);
        try {
            test.run();
            counts[0]++;
            System.out.printf("[PASS] [%s] %s%n", suite, name);
        } catch (Throwable t) {
            counts[1]++;
            String msg = String.format("[%s] %s -> %s: %s",
                    suite, name, t.getClass().getSimpleName(), t.getMessage());
            failures.add(msg);
            System.out.printf("[FAIL] %s%n", msg);
            t.printStackTrace(System.out);
        }
    }

    private void printSummaryAndExit() {
        System.out.println("------------------------------------------------------------------");
        int totalPass = 0;
        int totalFail = 0;
        for (Map.Entry<String, int[]> e : suiteCounts.entrySet()) {
            int p = e.getValue()[0];
            int f = e.getValue()[1];
            totalPass += p;
            totalFail += f;
            System.out.printf("  %-10s : %d passed, %d failed (total %d)%n", e.getKey(), p, f, p + f);
        }
        System.out.printf("  TOTAL      : %d passed, %d failed (total %d)%n",
                totalPass, totalFail, totalPass + totalFail);
        System.out.println("==================================================================");
        if (totalFail > 0) {
            System.err.println("FAILURES:");
            for (String f : failures) {
                System.err.println("  - " + f);
            }
            System.exit(1);
        }
    }

    // =========================================================================
    // SUITE A: CORE DOMAIN OOP & ENGINE
    // =========================================================================

    private void testPlayerEncapsulationMoneyJailBankruptcy() {
        Player p1 = new Player("u1", "Người chơi 1", 1_500_000);
        Player p2 = new Player("u2", "Người chơi 2", 1_000_000);

        assertEquals(0, p1.getPosition(), "Initial position");
        assertEquals(1_500_000, p1.getMoney(), "Initial money");
        assertEquals(PlayerStatus.ACTIVE, p1.getStatus(), "Initial status");
        assertEquals(5, p1.getInventory().getMaxCapacity(), "Default inventory capacity");

        p1.setPosition(-1);
        assertEquals(39, p1.getPosition(), "Negative position wraps to 39");
        p1.setPosition(43);
        assertEquals(3, p1.getPosition(), "Position >= 40 wraps modulo 40");

        p1.addMoney(-100_000);
        assertEquals(1_500_000, p1.getMoney(), "Negative addMoney ignored");
        p1.addMoney(250_000);
        assertEquals(1_750_000, p1.getMoney(), "Positive addMoney applied");

        assertFalse(p1.deductMoney(0), "Zero deduct returns false");
        assertFalse(p1.deductMoney(5_000_000), "Insufficient deduct returns false");
        assertEquals(1_750_000, p1.getMoney(), "Money unchanged on failed deduct");
        assertTrue(p1.deductMoney(250_000), "Valid deduct returns true");
        assertEquals(1_500_000, p1.getMoney(), "Money reduced after deduct");

        assertFalse(p1.pay(2_000_000, p2), "Insufficient pay fails");
        assertEquals(1_000_000, p2.getMoney(), "Receiver unchanged on failed pay");
        assertTrue(p1.pay(300_000, p2), "Valid pay succeeds");
        assertEquals(1_200_000, p1.getMoney(), "Payer balance after pay");
        assertEquals(1_300_000, p2.getMoney(), "Receiver balance after pay");

        // Move across GO while ACTIVE grants +$200,000
        p1.setPosition(35);
        p1.move(7);
        assertEquals(2, p1.getPosition(), "Position after move across GO");
        assertEquals(1_400_000, p1.getMoney(), "Passed GO bonus +$200,000");

        // Jail & bail
        p1.sendToJail(10);
        assertEquals(10, p1.getPosition(), "Jail position");
        assertEquals(PlayerStatus.IN_JAIL, p1.getStatus(), "Status IN_JAIL");
        p1.setPosition(38);
        p1.move(4); // Crossing GO while IN_JAIL must NOT award $200,000
        assertEquals(1_400_000, p1.getMoney(), "No GO bonus while IN_JAIL");

        assertTrue(p1.payBail(), "Pay bail $50,000 succeeds");
        assertEquals(PlayerStatus.ACTIVE, p1.getStatus(), "Active after bail");
        assertEquals(1_350_000, p1.getMoney(), "Money after $50,000 bail");
        assertFalse(p1.payBail(), "Pay bail when ACTIVE returns false");

        // Buy, transfer & bankruptcy
        StreetProperty street1 = new StreetProperty(1, "Phố Huế", 60_000, "BROWN", 10_000);
        StreetProperty street2 = new StreetProperty(3, "Hàng Bài", 80_000, "BROWN", 12_000);
        p1.buyProperty(street1);
        p1.buyProperty(street2);
        assertEquals(p1, street1.getOwner(), "p1 owns street1");
        assertEquals(2, p1.getOwnedProperties().size(), "p1 owns 2 properties");

        p1.transferProperty(street1, p2);
        assertEquals(p2, street1.getOwner(), "street1 transferred to p2");
        assertEquals(1, p1.getOwnedProperties().size(), "p1 owns 1 property");

        int p2BeforeBankruptcy = p2.getMoney();
        int p1Cash = p1.getMoney();
        p1.declareBankruptcy(p2);
        assertEquals(PlayerStatus.BANKRUPT, p1.getStatus(), "p1 status BANKRUPT");
        assertEquals(0, p1.getMoney(), "p1 money zeroed");
        assertEquals(0, p1.getOwnedProperties().size(), "p1 properties cleared");
        assertEquals(p2, street2.getOwner(), "Creditor inherits street2");
        assertEquals(p2BeforeBankruptcy + p1Cash, p2.getMoney(), "Creditor receives remaining cash");

        // Bankruptcy to bank (null creditor) clears ownership
        p2.declareBankruptcy(null);
        assertNull(street1.getOwner(), "Bank bankruptcy resets street1 owner");
        assertNull(street2.getOwner(), "Bank bankruptcy resets street2 owner");
    }

    private void testBoardAndTilePolymorphism() {
        Board board = new Board();
        assertEquals(40, board.getTiles().size(), "Board has 40 tiles");
        assertThrows(UnsupportedOperationException.class, () -> board.getTiles().add(new FreeParkingTile(99, "X")),
                "Board.getTiles() must be unmodifiable");
        assertEquals("GO", board.getTile(0).getName(), "Tile 0 is GO");
        assertEquals("GO", board.getTile(40).getName(), "Tile 40 wraps to GO");
        assertEquals(39, board.getTile(-1).getIndex(), "Tile -1 wraps to 39");

        GameRoom room = new GameRoom("r1", "Test", 2);
        Player p = new Player("p1", "Tester", 500_000);
        room.addPlayer(p);

        Tile go = board.getTile(0);
        assertTrue(go instanceof GoTile, "Tile 0 is GoTile");
        go.onLand(p, room);
        assertEquals(700_000, p.getMoney(), "GoTile awards $200,000");

        Tile parking = board.getTile(20);
        assertTrue(parking instanceof FreeParkingTile, "Tile 20 is FreeParkingTile");
        parking.onLand(p, room);
        assertEquals(700_000, p.getMoney(), "FreeParkingTile keeps money unchanged");

        Tile tax = board.getTile(4);
        assertTrue(tax instanceof TaxTile, "Tile 4 is TaxTile");
        tax.onLand(p, room);
        assertEquals(500_000, p.getMoney(), "TaxTile deducts $200,000");

        Tile goToJail = board.getTile(30);
        assertTrue(goToJail instanceof GoToJailTile, "Tile 30 is GoToJailTile");
        p.setPosition(30);
        goToJail.onLand(p, room);
        assertEquals(10, p.getPosition(), "GoToJail sends player to index 10");
        assertEquals(PlayerStatus.IN_JAIL, p.getStatus(), "GoToJail sets IN_JAIL");
        assertEquals(500_000, p.getMoney(), "GoToJail does NOT grant GO money");

        Tile jail = board.getTile(10);
        assertTrue(jail instanceof JailTile, "Tile 10 is JailTile");
        // 3 turns in jail triggers mandatory $50,000 bail
        jail.onLand(p, room);
        assertEquals(PlayerStatus.IN_JAIL, p.getStatus(), "Still in jail after turn 1");
        jail.onLand(p, room);
        assertEquals(PlayerStatus.IN_JAIL, p.getStatus(), "Still in jail after turn 2");
        jail.onLand(p, room);
        assertEquals(PlayerStatus.ACTIVE, p.getStatus(), "Released on 3rd turn via bail");
        assertEquals(450_000, p.getMoney(), "$50,000 bail deducted on 3rd jail turn");

        // Just visiting when ACTIVE
        jail.onLand(p, room);
        assertEquals(PlayerStatus.ACTIVE, p.getStatus(), "Just Visiting stays ACTIVE");
        assertEquals(450_000, p.getMoney(), "Just Visiting costs $0");

        // Tax bankruptcy
        Player broke = new Player("broke", "Broke", 50_000);
        tax.onLand(broke, room);
        assertEquals(PlayerStatus.BANKRUPT, broke.getStatus(), "Tax > cash causes BANKRUPT");
    }

    private void testPropertyRentAndModifiers() {
        GameRoom room = new GameRoom("r-rent", "Rent Room", 2);
        Player owner = new Player("owner", "Owner", 5_000_000);
        Player guest = new Player("guest", "Guest", 2_000_000);
        room.addPlayer(owner);
        room.addPlayer(guest);

        // StreetProperty rent progression
        StreetProperty street = new StreetProperty(1, "Street", 100_000, "BROWN", 10_000);
        assertEquals(0, street.calculateRent(room), "Unowned street rent = 0");
        owner.buyProperty(street);
        assertEquals(10_000, street.calculateRent(room), "Base street rent");
        street.setHouseCount(2);
        assertEquals(50_000, street.calculateRent(room), "2 houses rent = base * (1 + 2*2) = 50,000");
        street.setHasHotel(true);
        assertEquals(100_000, street.calculateRent(room), "Hotel rent = base * 10 = 100,000");

        // RailroadProperty rent progression
        RailroadProperty rr1 = new RailroadProperty(5, "RR1", 200_000);
        RailroadProperty rr2 = new RailroadProperty(15, "RR2", 200_000);
        RailroadProperty rr3 = new RailroadProperty(25, "RR3", 200_000);
        RailroadProperty rr4 = new RailroadProperty(35, "RR4", 200_000);
        owner.buyProperty(rr1);
        assertEquals(25_000, rr1.calculateRent(room), "1 railroad rent = 25,000");
        owner.buyProperty(rr2);
        assertEquals(50_000, rr1.calculateRent(room), "2 railroads rent = 50,000");
        owner.buyProperty(rr3);
        assertEquals(100_000, rr1.calculateRent(room), "3 railroads rent = 100,000");
        owner.buyProperty(rr4);
        assertEquals(200_000, rr1.calculateRent(room), "4 railroads rent = 200,000");

        // UtilityProperty rent calculation
        UtilityProperty u1 = new UtilityProperty(12, "Electric", 150_000);
        UtilityProperty u2 = new UtilityProperty(28, "Water", 150_000);
        owner.buyProperty(u1);
        Dice d = new Dice();
        d.roll();
        room.setLastDice(d);
        assertEquals(d.getTotal() * 4_000, u1.calculateRent(room), "1 utility rent = dice * 4,000");
        owner.buyProperty(u2);
        assertEquals(d.getTotal() * 10_000, u1.calculateRent(room), "2 utilities rent = dice * 10,000");

        // PropertyTile.onLand modifiers: shield, frozen, mortgaged, multiplier, bankruptcy
        int guestBefore = guest.getMoney();
        int ownerBefore = owner.getMoney();
        guest.setRentShieldActive(true);
        street.onLand(guest, room);
        assertFalse(guest.isRentShieldActive(), "Rent shield consumed on land");
        assertEquals(guestBefore, guest.getMoney(), "Rent shield waives rent");

        street.setFrozen(true);
        street.onLand(guest, room);
        assertEquals(guestBefore, guest.getMoney(), "Frozen property waives rent");
        street.setFrozen(false);

        street.setMortgaged(true);
        street.onLand(guest, room);
        assertEquals(guestBefore, guest.getMoney(), "Mortgaged property waives rent");
        street.setMortgaged(false);

        room.setRentMultiplier(3);
        street.onLand(guest, room);
        assertEquals(guestBefore - 300_000, guest.getMoney(), "3x multiplier rent deducted from guest");
        assertEquals(ownerBefore + 300_000, owner.getMoney(), "3x multiplier rent credited to owner");
        room.setRentMultiplier(1);

        // Insufficient rent triggers bankruptcy to owner
        Player poor = new Player("poor", "Poor", 50_000);
        street.onLand(poor, room);
        assertEquals(PlayerStatus.BANKRUPT, poor.getStatus(), "Insufficient rent causes bankruptcy");
    }

    private void testInventoryAndItemEffects() {
        Inventory inv = new Inventory(3);
        assertEquals(3, inv.getMaxCapacity(), "Inventory max capacity");
        assertNotNull(inv.getItems(), "Inventory.getItems() must never return null");
        assertThrows(UnsupportedOperationException.class, () -> inv.getItems().add(new MagicDiceItem()),
                "Inventory.getItems() must be unmodifiable");

        Item magic = new MagicDiceItem();
        Item shield = new RentShieldItem();
        Item dynamite = new DynamiteItem();
        Item warrant = new ArrestWarrantItem();
        assertTrue(inv.addItem(magic), "Add item 1");
        assertTrue(inv.addItem(shield), "Add item 2");
        assertTrue(inv.addItem(dynamite), "Add item 3");
        assertFalse(inv.addItem(warrant), "4th item rejected when capacity=3");
        assertTrue(inv.hasItem("MAGIC_DICE"), "hasItem MAGIC_DICE");
        assertNotNull(inv.getItemById("RENT_SHIELD"), "getItemById RENT_SHIELD");
        assertNotNull(inv.removeItem("DYNAMITE"), "removeItem DYNAMITE");
        assertFalse(inv.hasItem("DYNAMITE"), "DYNAMITE removed");
        assertNull(inv.removeItem("NON_EXISTENT"), "remove unknown returns null");

        GameRoom room = new GameRoom("r-items", "Item Room", 2);
        Player u1 = new Player("u1", "User1", 1_000_000);
        Player u2 = new Player("u2", "User2", 2_000_000);
        room.addPlayer(u1);
        room.addPlayer(u2);

        // MagicDiceItem
        assertFalse(magic.canUse(u1, room, new ItemTargetContext(null, null, 1)), "Dice 1 invalid");
        assertFalse(magic.canUse(u1, room, new ItemTargetContext(null, null, 13)), "Dice 13 invalid");
        assertTrue(magic.applyEffect(u1, room, new ItemTargetContext(null, null, 8)), "Dice 8 applied");
        assertEquals(Integer.valueOf(8), room.getForcedDiceValue(), "forcedDiceValue = 8");

        // RentShieldItem + HostileTakeoverItem + DynamiteItem
        StreetProperty u2Prop = (StreetProperty) room.getBoard().getTile(3);
        u2.buyProperty(u2Prop);
        u2Prop.setHouseCount(2);
        u1.setPosition(3);

        assertTrue(shield.applyEffect(u1, room, new ItemTargetContext()), "RentShield applied on opponent tile");
        assertTrue(u1.isRentShieldActive(), "RentShield flag active");
        u2Prop.onLand(u1, room);
        assertEquals(1_000_000, u1.getMoney(), "RentShield blocks rent");

        assertTrue(dynamite.applyEffect(u1, room, new ItemTargetContext(null, 3, null)), "Dynamite applied");
        assertEquals(0, u2Prop.getHouseCount(), "Dynamite destroyed houses");

        HostileTakeoverItem takeover = new HostileTakeoverItem();
        int u2MoneyBefore = u2.getMoney();
        assertTrue(takeover.applyEffect(u1, room, new ItemTargetContext(null, 3, null)), "HostileTakeover applied");
        assertEquals(u1, u2Prop.getOwner(), "HostileTakeover transferred ownership to u1");
        assertEquals(1_000_000 - u2Prop.getPrice(), u1.getMoney(), "HostileTakeover deducted price");
        assertEquals(u2MoneyBefore + u2Prop.getPrice(), u2.getMoney(), "HostileTakeover paid old owner");

        // FortuneSwapItem
        u1.setPosition(3);
        u2.setPosition(24);
        FortuneSwapItem swap = new FortuneSwapItem();
        assertTrue(swap.applyEffect(u1, room, new ItemTargetContext()), "FortuneSwap applied");
        assertEquals(24, u1.getPosition(), "u1 swapped to u2 position");
        assertEquals(3, u2.getPosition(), "u2 swapped to u1 position");

        // ArrestWarrantItem
        assertTrue(warrant.applyEffect(u1, room, new ItemTargetContext(u2.getId(), null, null)), "ArrestWarrant applied");
        assertEquals(10, u2.getPosition(), "u2 sent to jail pos 10");
        assertEquals(PlayerStatus.IN_JAIL, u2.getStatus(), "u2 IN_JAIL");
    }

    private void testGameEventManager() {
        GameRoom room = new GameRoom("r-ev", "Event Room", 2);
        Player p1 = new Player("p1", "P1", 1_000_000);
        Player p2 = new Player("p2", "P2", 2_000_000);
        room.addPlayer(p1);
        room.addPlayer(p2);

        GameEventManager manager = new GameEventManager();

        // StimulusDayEvent (+500,000)
        manager.triggerEvent(new StimulusDayEvent(), room);
        assertEquals(1_500_000, p1.getMoney(), "StimulusDay +500,000 p1");
        assertEquals(2_500_000, p2.getMoney(), "StimulusDay +500,000 p2");
        assertEquals(0, manager.getActiveEvents().size(), "Instant event not retained");

        // RichTaxEvent (40% of richest 2,500,000 = 1,000,000 -> to p1)
        manager.triggerEvent(new RichTaxEvent(), room);
        assertEquals(1_500_000, p2.getMoney(), "RichTax deducted 40%");
        assertEquals(2_500_000, p1.getMoney(), "RichTax transferred to other player");

        // EarthquakeEvent
        StreetProperty s1 = (StreetProperty) room.getBoard().getTile(1);
        p1.buyProperty(s1);
        s1.setHasHotel(true);
        manager.triggerEvent(new EarthquakeEvent(), room);
        assertFalse(s1.isHasHotel(), "Earthquake collapsed hotel");
        assertEquals(4, s1.getHouseCount(), "Hotel became 4 houses");

        // ContinentalDriftEvent
        StreetProperty s2 = (StreetProperty) room.getBoard().getTile(3);
        p2.buyProperty(s2);
        manager.triggerEvent(new ContinentalDriftEvent(), room);
        assertEquals(p2, s1.getOwner(), "ContinentalDrift swapped s1 to p2");
        assertEquals(p1, s2.getOwner(), "ContinentalDrift swapped s2 to p1");

        // Timed events: PropertyBoomEvent & FrozenTileEvent (duration 3)
        FrozenTileEvent frozen = new FrozenTileEvent();
        manager.triggerEvent(new PropertyBoomEvent(), room);
        manager.triggerEvent(frozen, room);
        assertEquals(3, room.getRentMultiplier(), "PropertyBoom sets 3x rent");
        assertEquals(3, frozen.getFrozenTileIndices().size(), "FrozenTile froze 3 tiles");
        assertEquals(2, manager.getActiveEvents().size(), "2 active timed events");

        manager.onTurnStart(room, p1);
        manager.onTurnStart(room, p2);
        assertEquals(3, room.getRentMultiplier(), "Still active on turn 2");
        manager.onTurnStart(room, p1);
        assertEquals(1, room.getRentMultiplier(), "PropertyBoom expired after 3 turns");
        assertEquals(0, manager.getActiveEvents().size(), "All timed events expired");
        for (Tile t : room.getBoard().getTiles()) {
            assertFalse(t.isFrozen(), "All tiles unfrozen after expire");
        }
    }

    private void testGameEngineValidationAndAntiCheat() {
        GameRoom room = new GameRoom("r-eng", "Engine Room", 2);
        Player p1 = new Player("p1", "P1", 1_500_000);
        Player p2 = new Player("p2", "P2", 1_500_000);
        room.addPlayer(p1);
        GameEngine engine = new GameEngine(room);

        // Cannot start with 1 player
        ActionResult failStart = engine.startGame("r-eng", "a1");
        assertFalse(failStart.isSuccess(), "Start with 1 player fails");
        assertEquals("NOT_ENOUGH_PLAYERS", failStart.getCode(), "Error code NOT_ENOUGH_PLAYERS");

        room.addPlayer(p2);
        ActionResult okStart = engine.startGame("r-eng", "a2");
        assertTrue(okStart.isSuccess(), "Start with 2 players succeeds");
        assertEquals(RoomStatus.PLAYING, room.getStatus(), "Room status PLAYING");

        // Anti-cheat validations
        assertEquals("INVALID_ROOM", engine.rollDice("wrong", "p1", 1, "a3").getCode(), "Invalid room");
        assertEquals("INVALID_ACTION_ID", engine.rollDice("r-eng", "p1", 1, "  ").getCode(), "Blank actionId");
        assertEquals("NOT_YOUR_TURN", engine.rollDice("r-eng", "p2", 1, "a4").getCode(), "Out-of-turn rejected");
        assertEquals("STALE_TURN", engine.rollDice("r-eng", "p1", 99, "a5").getCode(), "Stale turn rejected");

        // Valid roll & idempotent duplicate actionId
        ActionResult r1 = engine.rollDice("r-eng", "p1", 1, "roll-1");
        assertTrue(r1.isSuccess(), "p1 roll succeeds");
        ActionResult r1Dup = engine.rollDice("r-eng", "p1", 1, "roll-1");
        assertEquals(r1.getCode(), r1Dup.getCode(), "Duplicate actionId returns cached result");
        assertEquals("ACTION_ID_REUSED", engine.endTurn("r-eng", "p1", 1, "roll-1").getCode(),
                "Reused actionId for different action rejected");

        // Verify dice range and phase
        assertNotNull(room.getLastDice(), "LastDice recorded");
        assertTrue(room.getLastDice().getDice1() >= 1 && room.getLastDice().getDice1() <= 6, "dice1 1..6");
        assertTrue(room.getLastDice().getDice2() >= 1 && room.getLastDice().getDice2() <= 6, "dice2 1..6");

        if (room.getStatus() == RoomStatus.PLAYING && room.getCurrentPlayer().getId().equals("p1")) {
            assertEquals("INVALID_PHASE", engine.rollDice("r-eng", "p1", 1, "roll-again").getCode(),
                    "Double roll in AWAITING_DECISION rejected");
            ActionResult endRes = engine.endTurn("r-eng", "p1", 1, "end-1");
            assertTrue(endRes.isSuccess(), "endTurn succeeds");
        }
    }

    private void testGameEngineConcurrency() throws Exception {
        GameRoom room = new GameRoom("r-conc", "Concurrency Room", 2);
        Player p1 = new Player("p1", "P1", 1_500_000);
        Player p2 = new Player("p2", "P2", 1_500_000);
        room.addPlayer(p1);
        room.addPlayer(p2);
        GameEngine engine = new GameEngine(room);
        assertTrue(engine.startGame("r-conc", "start-1").isSuccess(), "Game started");

        int threads = 8;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger();
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            String actor = (idx % 2 == 0) ? "p1" : "p2";
            new Thread(() -> {
                ready.countDown();
                try {
                    go.await(3, TimeUnit.SECONDS);
                    ActionResult res = engine.rollDice("r-conc", actor, 1, "conc-" + idx);
                    if (res.isSuccess()) {
                        successCount.incrementAndGet();
                    }
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    done.countDown();
                }
            }).start();
        }

        assertTrue(ready.await(3, TimeUnit.SECONDS), "All threads ready");
        go.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS), "All threads finished");
        assertEquals(0, errors.size(), "Zero uncaught exceptions under concurrent rollDice");
        assertEquals(1, successCount.get(), "Exactly ONE rollDice call may succeed per turn");
    }

    private void testBotControllerAndTurnTimerService() throws Exception {
        // 1. Direct BotController turn
        GameRoom botRoom = new GameRoom("r-bot", "Bot Room", 2);
        Player bot = new Player("bot1", "Bot", 2_000_000);
        bot.setBot(true);
        Player human = new Player("h1", "Human", 1_500_000);
        botRoom.addPlayer(bot);
        botRoom.addPlayer(human);

        GameEngine botEngine = new GameEngine(botRoom);
        assertTrue(botEngine.startGame("r-bot", "s1").isSuccess(), "Bot room started");
        BotController.processBotTurn(botEngine);
        assertEquals("h1", botRoom.getCurrentPlayer().getId(), "Bot completed turn and passed to human");
        assertEquals(2, botRoom.getTurnId(), "TurnId incremented after bot turn");

        // 2. TurnTimerService 1-second timeout + cancel + shutdown
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        TurnTimerService timerService = new TurnTimerService(scheduler, 1);
        try {
            GameRoom timerRoom = new GameRoom("r-timer", "Timer Room", 2);
            Player p1 = new Player("p1", "P1", 1_500_000);
            Player p2 = new Player("p2", "P2", 1_500_000);
            timerRoom.addPlayer(p1);
            timerRoom.addPlayer(p2);
            GameEngine timerEngine = new GameEngine(timerRoom);

            CountDownLatch timeoutFired = new CountDownLatch(1);
            timerEngine.addEventListener((EngineEvent ev) -> {
                if (ev.getType() == EngineEvent.Type.TURN_TIMED_OUT) {
                    timeoutFired.countDown();
                }
            });

            assertTrue(timerEngine.startGame("r-timer", "s2").isSuccess(), "Timer room started");
            timerService.startTurnTimer("r-timer", timerRoom.getTurnId(), timerEngine);
            assertTrue(timeoutFired.await(4, TimeUnit.SECONDS), "TurnTimerService fired timeout within 4s");

            // Wait briefly for BotController takeover + turn advance
            long deadline = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < deadline && timerRoom.getTurnId() == 1) {
                Thread.sleep(50);
            }
            assertTrue(timerRoom.getTurnId() >= 2, "BotController advanced turn after timeout");
            assertEquals("p2", timerRoom.getCurrentPlayer().getId(), "Current player switched to p2");

            // Cancel timer prevents firing
            timerService.cancelTurnTimer("r-timer");
        } finally {
            timerService.shutdown();
        }
    }

    // =========================================================================
    // SUITE B: CLOUDFLARE DEV/PROD ORIGIN PROTECTION VIA LIVE API GATEWAY
    // =========================================================================

    private void testCloudflareDevModeOriginBypass() throws Exception {
        // Verify GatewayConfig defaults to dev (Cloudflare bypass) even when ORIGIN_SECRET_KEY is set without APP_ENV/MONOPOLY_ENV
        GatewayConfig defaultCfg = GatewayConfig.fromArgsAndEnv(
                new String[0],
                Map.of("ORIGIN_SECRET_KEY", "some-env-secret"),
                null
        );
        assertEquals("dev", defaultCfg.getMode(), "Default mode without APP_ENV/MONOPOLY_ENV must be dev");
        assertFalse(defaultCfg.createOriginFilter().isProtectionEnabled(),
                "Default dev mode must bypass Cloudflare even when ORIGIN_SECRET_KEY is present");

        try (GatewayHarness gw = startGateway("dev", null)) {
            int port = gw.port;

            // HTTP GET /health without X-Origin-Verify -> 200
            HttpResponse health = RawHttpClient.request("127.0.0.1", port, "GET", "/health", null, null);
            assertEquals(200, health.statusCode, "DEV /health without X-Origin-Verify returns 200");
            Map<String, Object> hJson = MiniJson.parseObject(health.body);
            assertEquals("UP", hJson.get("status"), "/health status UP");
            assertEquals("dev", hJson.get("mode"), "/health mode dev");
            assertEquals(Boolean.FALSE, hJson.get("cloudflareProtectionEnabled"), "CF disabled in dev");

            // HTTP POST /api/v1/auth/register without X-Origin-Verify -> 201
            String regBody = "{\"username\":\"devuser1\",\"password\":\"secret123\",\"displayName\":\"Dev User\"}";
            HttpResponse reg = RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/auth/register", null, regBody);
            assertEquals(201, reg.statusCode, "DEV register without X-Origin-Verify returns 201");

            // WebSocket /ws upgrade without X-Origin-Verify -> 101
            WsUpgradeResult ws = StompWsClient.upgradeOnly("127.0.0.1", port, "/ws", null);
            try {
                assertEquals(101, ws.statusCode, "DEV /ws without X-Origin-Verify returns 101");
                assertEquals(ws.expectedAccept, ws.headers.get("sec-websocket-accept"),
                        "Valid RFC 6455 Sec-WebSocket-Accept");
            } finally {
                ws.closeQuietly();
            }
        }
    }

    private void testCloudflareProdModeOriginShield() throws Exception {
        try (GatewayHarness gw = startGateway("prod", PROD_ORIGIN_SECRET)) {
            int port = gw.port;

            // 1. HTTP without X-Origin-Verify -> 403 FORBIDDEN_ORIGIN
            HttpResponse noHeader = RawHttpClient.request("127.0.0.1", port, "GET", "/health", null, null);
            assertEquals(403, noHeader.statusCode, "PROD /health without header returns 403");
            assertEquals("FORBIDDEN_ORIGIN", extractErrorCode(noHeader.body), "Error code FORBIDDEN_ORIGIN");

            // 2. HTTP with wrong X-Origin-Verify -> 403 FORBIDDEN_ORIGIN
            Map<String, String> wrongHdr = Map.of("X-Origin-Verify", "wrong-secret");
            HttpResponse wrongRes = RawHttpClient.request("127.0.0.1", port, "GET", "/health", wrongHdr, null);
            assertEquals(403, wrongRes.statusCode, "PROD /health wrong header returns 403");
            assertEquals("FORBIDDEN_ORIGIN", extractErrorCode(wrongRes.body), "Error code FORBIDDEN_ORIGIN on wrong key");

            // 3. CORS preflight OPTIONS works even without origin header -> 204
            HttpResponse opt = RawHttpClient.request("127.0.0.1", port, "OPTIONS", "/api/v1/rooms", null, null);
            assertEquals(204, opt.statusCode, "PROD OPTIONS preflight returns 204");

            // 4. WebSocket /ws without X-Origin-Verify -> 403
            WsUpgradeResult wsMissing = StompWsClient.upgradeOnly("127.0.0.1", port, "/ws", null);
            try {
                assertEquals(403, wsMissing.statusCode, "PROD /ws without header returns 403");
            } finally {
                wsMissing.closeQuietly();
            }

            // 5. WebSocket /ws with wrong X-Origin-Verify -> 403
            WsUpgradeResult wsWrong = StompWsClient.upgradeOnly("127.0.0.1", port, "/ws", wrongHdr);
            try {
                assertEquals(403, wsWrong.statusCode, "PROD /ws wrong header returns 403");
            } finally {
                wsWrong.closeQuietly();
            }

            // 6. Valid X-Origin-Verify SUCCEEDS for HTTP & WebSocket
            Map<String, String> validHdr = Map.of("X-Origin-Verify", PROD_ORIGIN_SECRET);
            HttpResponse okHealth = RawHttpClient.request("127.0.0.1", port, "GET", "/health", validHdr, null);
            assertEquals(200, okHealth.statusCode, "PROD /health valid header returns 200");
            Map<String, Object> hJson = MiniJson.parseObject(okHealth.body);
            assertEquals("prod", hJson.get("mode"), "/health mode prod");
            assertEquals(Boolean.TRUE, hJson.get("cloudflareProtectionEnabled"), "CF enabled in prod");

            HttpResponse okReg = RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/auth/register",
                    validHdr, "{\"username\":\"produser\",\"password\":\"secret123\",\"displayName\":\"Prod User\"}");
            assertEquals(201, okReg.statusCode, "PROD register valid header returns 201");

            WsUpgradeResult wsValid = StompWsClient.upgradeOnly("127.0.0.1", port, "/ws", validHdr);
            try {
                assertEquals(101, wsValid.statusCode, "PROD /ws valid header returns 101");
                assertEquals(wsValid.expectedAccept, wsValid.headers.get("sec-websocket-accept"),
                        "Valid WS accept in PROD");
            } finally {
                wsValid.closeQuietly();
            }
        }
    }

    // =========================================================================
    // SUITE C: FULL REST API & SECURITY HARDENING OVER REAL TCP HTTP
    // =========================================================================

    private void testRestSecurityHardening() throws Exception {
        try (GatewayHarness gw = startGateway("dev", null)) {
            int port = gw.port;

            // GET /health
            HttpResponse health = RawHttpClient.request("127.0.0.1", port, "GET", "/health", null, null);
            assertEquals(200, health.statusCode, "GET /health 200");
            Map<String, Object> healthJson = MiniJson.parseObject(health.body);
            assertEquals("dev", healthJson.get("mode"), "Suite C runs in dev mode");
            assertEquals(Boolean.FALSE, healthJson.get("cloudflareProtectionEnabled"),
                    "Suite C /health confirms cloudflareProtectionEnabled=false");

            // OPTIONS CORS preflight
            HttpResponse opt = RawHttpClient.request("127.0.0.1", port, "OPTIONS", "/api/v1/rooms", null, null);
            assertEquals(204, opt.statusCode, "OPTIONS returns 204");
            assertEquals("*", opt.headers.get("access-control-allow-origin"), "CORS Allow-Origin *");
            assertTrue(opt.headers.getOrDefault("access-control-allow-methods", "").contains("POST"),
                    "CORS Allow-Methods contains POST");
            assertTrue(opt.headers.getOrDefault("access-control-allow-headers", "").toLowerCase(Locale.ROOT)
                    .contains("authorization"), "CORS Allow-Headers contains Authorization");

            // 405 on DELETE and PUT
            HttpResponse del = RawHttpClient.request("127.0.0.1", port, "DELETE", "/api/v1/rooms", null, null);
            assertEquals(405, del.statusCode, "DELETE returns 405");
            HttpResponse put = RawHttpClient.request("127.0.0.1", port, "PUT", "/api/v1/rooms", null, "{}");
            assertEquals(405, put.statusCode, "PUT returns 405");

            // 413 on >100KB payload
            String big = "{\"data\":\"" + "A".repeat(105_000) + "\"}";
            HttpResponse tooBig = RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/auth/login", null, big);
            assertEquals(413, tooBig.statusCode, "Payload >100KB returns 413");

            // 404 on unknown path
            HttpResponse notFound = RawHttpClient.request("127.0.0.1", port, "GET", "/api/v1/no-such-route", null, null);
            assertEquals(404, notFound.statusCode, "Unknown path returns 404");

            // 415 on non-JSON Content-Type for POST
            HttpResponse badMedia = RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/auth/login",
                    Map.of("Content-Type", "text/plain"), "hello");
            assertEquals(415, badMedia.statusCode, "Non-JSON POST Content-Type returns 415");

            // 401 on protected /api/v1/rooms without JWT
            HttpResponse unauthRooms = RawHttpClient.request("127.0.0.1", port, "GET", "/api/v1/rooms", null, null);
            assertEquals(401, unauthRooms.statusCode, "GET /api/v1/rooms without JWT returns 401");
        }
    }

    private void testRestAuthLifecycle() throws Exception {
        try (GatewayHarness gw = startGateway("dev", null)) {
            int port = gw.port;

            // Register user 1 with UTF-8 Vietnamese displayName
            String reg1Body = "{\"username\":\"alice99\",\"password\":\"pass1234\",\"displayName\":\"Nguyễn Ánh\"}";
            HttpResponse reg1 = RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/auth/register", null, reg1Body);
            assertEquals(201, reg1.statusCode, "Register alice99 -> 201");
            Map<String, Object> msg1 = MiniJson.parseObject(reg1.body);
            assertEquals("AUTH_REGISTERED", msg1.get("type"), "Type AUTH_REGISTERED");
            Map<String, Object> p1 = MiniJson.parseObject((String) msg1.get("payload"));
            assertNotNull(p1.get("accessToken"), "Register returns accessToken");
            assertEquals("Nguyễn Ánh", p1.get("displayName"), "UTF-8 displayName preserved");

            // Register user 2
            String reg2Body = "{\"username\":\"bob99\",\"password\":\"pass5678\",\"displayName\":\"Trần Bảo\"}";
            HttpResponse reg2 = RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/auth/register", null, reg2Body);
            assertEquals(201, reg2.statusCode, "Register bob99 -> 201");

            // Duplicate username -> 409
            HttpResponse dup = RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/auth/register", null, reg1Body);
            assertEquals(409, dup.statusCode, "Duplicate username returns 409");
            assertEquals("USERNAME_TAKEN", extractErrorCode(dup.body), "Error USERNAME_TAKEN");

            // Weak password -> 400
            HttpResponse weak = RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/auth/register", null,
                    "{\"username\":\"charlie\",\"password\":\"123\",\"displayName\":\"Charlie\"}");
            assertEquals(400, weak.statusCode, "Weak password returns 400");
            assertEquals("WEAK_PASSWORD", extractErrorCode(weak.body), "Error WEAK_PASSWORD");

            // Login success -> 200
            HttpResponse loginOk = RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/auth/login", null,
                    "{\"username\":\"alice99\",\"password\":\"pass1234\"}");
            assertEquals(200, loginOk.statusCode, "Login valid -> 200");
            Map<String, Object> loginPayload = MiniJson.parseObject((String) MiniJson.parseObject(loginOk.body).get("payload"));
            String token = (String) loginPayload.get("accessToken");
            assertNotNull(token, "Login returns accessToken");

            // Wrong password -> 401
            HttpResponse loginFail = RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/auth/login", null,
                    "{\"username\":\"alice99\",\"password\":\"wrongpass\"}");
            assertEquals(401, loginFail.statusCode, "Wrong password returns 401");
            assertEquals("INVALID_CREDENTIALS", extractErrorCode(loginFail.body), "Error INVALID_CREDENTIALS");

            // /api/v1/auth/me with valid JWT -> 200
            HttpResponse meOk = RawHttpClient.request("127.0.0.1", port, "GET", "/api/v1/auth/me",
                    Map.of("Authorization", "Bearer " + token), null);
            assertEquals(200, meOk.statusCode, "/auth/me valid JWT -> 200");
            Map<String, Object> mePayload = MiniJson.parseObject((String) MiniJson.parseObject(meOk.body).get("payload"));
            assertEquals("alice99", mePayload.get("username"), "/auth/me returns alice99");
            assertEquals("Nguyễn Ánh", mePayload.get("displayName"), "/auth/me UTF-8 displayName");

            // /api/v1/auth/me without JWT -> 401
            HttpResponse meMissing = RawHttpClient.request("127.0.0.1", port, "GET", "/api/v1/auth/me", null, null);
            assertEquals(401, meMissing.statusCode, "/auth/me missing JWT -> 401");

            // /api/v1/auth/me with invalid JWT -> 401
            HttpResponse meBad = RawHttpClient.request("127.0.0.1", port, "GET", "/api/v1/auth/me",
                    Map.of("Authorization", "Bearer invalid.jwt.token"), null);
            assertEquals(401, meBad.statusCode, "/auth/me invalid JWT -> 401");
        }
    }

    private void testRestRoomLifecycle() throws Exception {
        try (GatewayHarness gw = startGateway("dev", null)) {
            int port = gw.port;

            AuthUser u1 = registerViaRest(port, "hostUser", "pass1234", "Chủ Phòng");
            AuthUser u2 = registerViaRest(port, "guestUser", "pass1234", "Khách Mời");
            Map<String, String> h1 = Map.of("Authorization", "Bearer " + u1.token);
            Map<String, String> h2 = Map.of("Authorization", "Bearer " + u2.token);

            // Create room (also pass spoofed hostUserId to confirm authoritative JWT enforcement)
            HttpResponse createRes = RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms", h1,
                    "{\"roomName\":\"Bàn Hà Nội\",\"maxPlayers\":4,\"hostUserId\":\"spoofed-id\"}");
            assertEquals(201, createRes.statusCode, "Create room -> 201");
            Map<String, Object> createMsg = MiniJson.parseObject(createRes.body);
            assertEquals("ROOM_CREATED", createMsg.get("type"), "Type ROOM_CREATED");
            Map<String, Object> roomSummary = MiniJson.parseObject((String) createMsg.get("payload"));
            String roomId = (String) roomSummary.get("roomId");
            assertNotNull(roomId, "roomId present");
            assertEquals(u1.userId, roomSummary.get("hostId"), "Authoritative hostId from JWT");
            assertEquals(1L, ((Number) roomSummary.get("currentPlayers")).longValue(), "1 player in new room");

            // List rooms
            HttpResponse listRes = RawHttpClient.request("127.0.0.1", port, "GET", "/api/v1/rooms", h1, null);
            assertEquals(200, listRes.statusCode, "List rooms -> 200");
            List<Object> rooms = MiniJson.parseArray((String) MiniJson.parseObject(listRes.body).get("payload"));
            assertEquals(1, rooms.size(), "1 room in list");

            // Get snapshot
            HttpResponse snapRes = RawHttpClient.request("127.0.0.1", port, "GET", "/api/v1/rooms/" + roomId + "/snapshot", h1, null);
            assertEquals(200, snapRes.statusCode, "Get snapshot -> 200");
            Map<String, Object> snap = MiniJson.parseObject((String) MiniJson.parseObject(snapRes.body).get("payload"));
            assertEquals("WAITING", snap.get("roomStatus"), "Snapshot roomStatus WAITING");
            assertEquals(40, ((List<?>) snap.get("tiles")).size(), "Snapshot contains 40 tiles");

            // User 2 joins
            HttpResponse joinRes = RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms/" + roomId + "/join", h2, "{}");
            assertEquals(200, joinRes.statusCode, "User 2 joins -> 200");
            Map<String, Object> joinSummary = MiniJson.parseObject((String) MiniJson.parseObject(joinRes.body).get("payload"));
            assertEquals(2L, ((Number) joinSummary.get("currentPlayers")).longValue(), "2 players after join");

            // Non-host start rejected 403 FORBIDDEN_NOT_HOST
            HttpResponse badStart = RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms/" + roomId + "/start", h2, "{}");
            assertEquals(403, badStart.statusCode, "Non-host start -> 403");
            assertEquals("FORBIDDEN_NOT_HOST", extractErrorCode(badStart.body), "Error FORBIDDEN_NOT_HOST");

            // Host start succeeds 200 GAME_STARTED
            HttpResponse okStart = RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms/" + roomId + "/start", h1, "{}");
            assertEquals(200, okStart.statusCode, "Host start -> 200");
            assertEquals("GAME_STARTED", MiniJson.parseObject(okStart.body).get("type"), "Type GAME_STARTED");

            // Leave room test on a second waiting room
            HttpResponse r2Res = RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms", h1,
                    "{\"roomName\":\"Leave Room\",\"maxPlayers\":3}");
            assertEquals(201, r2Res.statusCode, "Create room 2 -> 201");
            String r2Id = (String) MiniJson.parseObject((String) MiniJson.parseObject(r2Res.body).get("payload")).get("roomId");

            assertEquals(200, RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms/" + r2Id + "/join", h2, "{}").statusCode,
                    "u2 joins room 2");
            HttpResponse leave2 = RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms/" + r2Id + "/leave", h2, "{}");
            assertEquals(200, leave2.statusCode, "u2 leaves room 2 -> 200");
            assertEquals("ROOM_LEFT", MiniJson.parseObject(leave2.body).get("type"), "Type ROOM_LEFT");

            HttpResponse leave1 = RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms/" + r2Id + "/leave", h1, "{}");
            assertEquals(200, leave1.statusCode, "Last player leaves room 2 -> 200");
            assertEquals("ROOM_DELETED", MiniJson.parseObject(leave1.body).get("type"), "Type ROOM_DELETED");
            assertEquals(404, RawHttpClient.request("127.0.0.1", port, "GET", "/api/v1/rooms/" + r2Id, h1, null).statusCode,
                    "Deleted room returns 404");
        }
    }

    // =========================================================================
    // SUITE D: REAL WEBSOCKET (RFC 6455) + STOMP 1.2 MULTIPLAYER & ANTI-CHEAT
    // =========================================================================

    private void testStompUnauthorizedConnect() throws Exception {
        try (GatewayHarness gw = startGateway("dev", null)) {
            int port = gw.port;

            // Missing Authorization header
            try (StompWsClient unauth = new StompWsClient("127.0.0.1", port, "/ws", null)) {
                StompFrame reply = unauth.connectAndExpectFrame(null, 4000);
                assertNotNull(reply, "Server must reply before closing");
                assertEquals("ERROR", reply.command, "Missing token rejected with STOMP ERROR");
            }

            // Invalid Bearer token
            try (StompWsClient bad = new StompWsClient("127.0.0.1", port, "/ws", null)) {
                StompFrame reply = bad.connectAndExpectFrame("Bearer bad.jwt.token", 4000);
                assertNotNull(reply, "Server must reply to invalid token");
                assertEquals("ERROR", reply.command, "Invalid token rejected with STOMP ERROR");
            }
        }
    }

    private void testStompMultiplayerAndAntiCheat() throws Exception {
        try (GatewayHarness gw = startGateway("dev", null)) {
            int port = gw.port;

            AuthUser p1 = registerViaRest(port, "stompP1", "pass1234", "Player 1");
            AuthUser p2 = registerViaRest(port, "stompP2", "pass1234", "Player 2");
            Map<String, String> h1 = Map.of("Authorization", "Bearer " + p1.token);
            Map<String, String> h2 = Map.of("Authorization", "Bearer " + p2.token);

            // Create, join, start room via REST
            HttpResponse createRes = RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms", h1,
                    "{\"roomName\":\"STOMP Arena\",\"maxPlayers\":2}");
            assertEquals(201, createRes.statusCode, "Create room -> 201");
            String roomId = (String) MiniJson.parseObject((String) MiniJson.parseObject(createRes.body).get("payload")).get("roomId");

            assertEquals(200, RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms/" + roomId + "/join", h2, "{}").statusCode,
                    "p2 joins");
            assertEquals(200, RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms/" + roomId + "/start", h1, "{}").statusCode,
                    "p1 starts");

            try (StompWsClient c1 = new StompWsClient("127.0.0.1", port, "/ws", null);
                 StompWsClient c2 = new StompWsClient("127.0.0.1", port, "/ws", null)) {

                StompFrame conn1 = c1.connectAndExpectFrame("Bearer " + p1.token, 4000);
                assertEquals("CONNECTED", conn1.command, "p1 CONNECTED");
                StompFrame conn2 = c2.connectAndExpectFrame("Bearer " + p2.token, 4000);
                assertEquals("CONNECTED", conn2.command, "p2 CONNECTED");

                String topicDest = "/topic/room/" + roomId;
                String err1Dest = "/queue/player/" + p1.userId + "/errors";
                String err2Dest = "/queue/player/" + p2.userId + "/errors";

                c1.subscribe("sub-room-1", topicDest);
                c1.subscribe("sub-err-1", err1Dest);
                c2.subscribe("sub-room-2", topicDest);
                c2.subscribe("sub-err-2", err2Dest);

                // Both receive initial GAME_STATE_SNAPSHOT
                Map<String, Object> snap1 = awaitGameSnapshot(c1, topicDest, 4000);
                Map<String, Object> snap2 = awaitGameSnapshot(c2, topicDest, 4000);
                assertEquals("PLAYING", snap1.get("roomStatus"), "c1 snapshot PLAYING");
                assertEquals("PLAYING", snap2.get("roomStatus"), "c2 snapshot PLAYING");
                assertTrue(((Number) snap1.get("turnDeadlineEpochMs")).longValue() > 0L,
                        "Snapshot turnDeadlineEpochMs > 0");
                assertTrue(((Number) snap1.get("serverTimeEpochMs")).longValue() > 0L,
                        "Snapshot serverTimeEpochMs > 0");
                assertTrue(snap1.get("logs") instanceof List<?> && !((List<?>) snap1.get("logs")).isEmpty(),
                        "Snapshot logs list present and non-empty");

                String activeId = (String) snap1.get("currentPlayerId");
                int turnId = ((Number) snap1.get("turnId")).intValue();
                assertEquals(activeId, snap2.get("currentPlayerId"), "Both clients agree on currentPlayerId");

                StompWsClient activeClient = activeId.equals(p1.userId) ? c1 : c2;
                String activeErrDest = activeId.equals(p1.userId) ? err1Dest : err2Dest;
                StompWsClient otherClient = activeId.equals(p1.userId) ? c2 : c1;
                String otherErrDest = activeId.equals(p1.userId) ? err2Dest : err1Dest;

                // 1. Out-of-turn roll-dice -> NOT_YOUR_TURN on /queue/player/{other}/errors
                otherClient.send("/app/room/" + roomId + "/roll-dice", "{\"turnId\":" + turnId + "}");
                Map<String, Object> outTurnErr = awaitGameError(otherClient, otherErrDest, 4000);
                assertEquals("NOT_YOUR_TURN", outTurnErr.get("error"), "Out-of-turn rejected with NOT_YOUR_TURN");

                // 2. Spoofed playerId overridden by JWT session.userId -> NOT_YOUR_TURN
                otherClient.send("/app/room/" + roomId + "/roll-dice",
                        "{\"playerId\":\"" + activeId + "\",\"turnId\":" + turnId + "}");
                Map<String, Object> spoofErr = awaitGameError(otherClient, otherErrDest, 4000);
                assertEquals("NOT_YOUR_TURN", spoofErr.get("error"), "Spoofed playerId overridden and rejected");

                // 3. Stale turnId -> STALE_TURN
                activeClient.send("/app/room/" + roomId + "/roll-dice", "{\"turnId\":" + (turnId + 99) + "}");
                Map<String, Object> staleErr = awaitGameError(activeClient, activeErrDest, 4000);
                assertEquals("STALE_TURN", staleErr.get("error"), "Stale turnId rejected with STALE_TURN");

                // 3b. Pay bail while ACTIVE -> PLAYER_NOT_IN_JAIL
                activeClient.send("/app/room/" + roomId + "/pay-bail", "{\"turnId\":" + turnId + "}");
                Map<String, Object> bailErr = awaitGameError(activeClient, activeErrDest, 4000);
                assertEquals("PLAYER_NOT_IN_JAIL", bailErr.get("error"), "Pay bail when ACTIVE rejected with PLAYER_NOT_IN_JAIL");

                // 3c. Forbidden subscribe to another player's error queue -> STOMP ERROR
                String otherUserId = activeId.equals(p1.userId) ? p2.userId : p1.userId;
                try (StompWsClient snooper = new StompWsClient("127.0.0.1", port, "/ws", null)) {
                    String activeTok = activeId.equals(p1.userId) ? p1.token : p2.token;
                    assertEquals("CONNECTED", snooper.connectAndExpectFrame("Bearer " + activeTok, 4000).command,
                            "Snooper CONNECTED");
                    snooper.subscribe("sub-bad", "/queue/player/" + otherUserId + "/errors");
                    StompFrame subErr = snooper.awaitAndConsume(f -> "ERROR".equals(f.command), 4000);
                    assertNotNull(subErr, "Subscribing to another player's error queue returns STOMP ERROR");
                }

                // 4. Valid roll -> DICE_ROLLED + consistent GAME_STATE_SNAPSHOT on BOTH clients,
                // plus buy-property (TILE_MISMATCH + valid buy when on unowned property) & end-turn
                boolean testedBuyProperty = false;
                boolean testedEndTurn = false;

                for (int step = 0; step < 8 && !(testedBuyProperty && testedEndTurn); step++) {
                    String curPlayerId = (String) snap1.get("currentPlayerId");
                    int curTurnId = ((Number) snap1.get("turnId")).intValue();
                    String curPhase = (String) snap1.get("phase");
                    StompWsClient curClient = curPlayerId.equals(p1.userId) ? c1 : c2;
                    String curErrDest = curPlayerId.equals(p1.userId) ? err1Dest : err2Dest;

                    Map<String, Object> curPlayerObj = findPlayerInSnapshot(snap1, curPlayerId);
                    String curStatus = (String) curPlayerObj.get("status");

                    if ("IN_JAIL".equals(curStatus)) {
                        curClient.send("/app/room/" + roomId + "/pay-bail", "{\"turnId\":" + curTurnId + "}");
                        snap1 = awaitGameSnapshot(c1, topicDest, 4000);
                        snap2 = awaitGameSnapshot(c2, topicDest, 4000);
                        assertSnapshotsConsistent(snap1, snap2);
                        continue;
                    }

                    if ("AWAITING_ROLL".equals(curPhase)) {
                        curClient.send("/app/room/" + roomId + "/roll-dice", "{\"turnId\":" + curTurnId + "}");

                        Map<String, Object> r1 = awaitTopicMessageOfType(c1, topicDest, "DICE_ROLLED", 4000);
                        Map<String, Object> r2 = awaitTopicMessageOfType(c2, topicDest, "DICE_ROLLED", 4000);
                        assertEquals(r1.get("payload"), r2.get("payload"), "Both clients receive identical DICE_ROLLED");

                        snap1 = awaitGameSnapshot(c1, topicDest, 4000);
                        snap2 = awaitGameSnapshot(c2, topicDest, 4000);
                        assertSnapshotsConsistent(snap1, snap2);
                        if ("FINISHED".equals(snap1.get("roomStatus"))) {
                            break;
                        }
                    }

                    // Check if current player landed in AWAITING_DECISION on an unowned property
                    curPhase = (String) snap1.get("phase");
                    curPlayerId = (String) snap1.get("currentPlayerId");
                    curTurnId = ((Number) snap1.get("turnId")).intValue();
                    curClient = curPlayerId.equals(p1.userId) ? c1 : c2;
                    curErrDest = curPlayerId.equals(p1.userId) ? err1Dest : err2Dest;

                    if ("AWAITING_DECISION".equals(curPhase)) {
                        curPlayerObj = findPlayerInSnapshot(snap1, curPlayerId);
                        int pos = ((Number) curPlayerObj.get("position")).intValue();
                        long money = ((Number) curPlayerObj.get("money")).longValue();
                        Map<String, Object> tileObj = findTileInSnapshot(snap1, pos);
                        long price = tileObj.get("price") instanceof Number ? ((Number) tileObj.get("price")).longValue() : 0L;
                        Object ownerId = tileObj.get("ownerId");

                        if (!testedBuyProperty && price > 0 && (ownerId == null || "".equals(ownerId)) && money >= price) {
                            // Wrong tileIndex -> TILE_MISMATCH
                            int wrongTile = (pos + 1) % 40;
                            curClient.send("/app/room/" + roomId + "/buy-property",
                                    "{\"turnId\":" + curTurnId + ",\"tileIndex\":" + wrongTile + "}");
                            Map<String, Object> mismatchErr = awaitGameError(curClient, curErrDest, 4000);
                            assertEquals("TILE_MISMATCH", mismatchErr.get("error"), "Wrong tileIndex rejected TILE_MISMATCH");

                            // Valid tileIndex -> PROPERTY_BOUGHT + snapshot
                            curClient.send("/app/room/" + roomId + "/buy-property",
                                    "{\"turnId\":" + curTurnId + ",\"tileIndex\":" + pos + "}");
                            Map<String, Object> bought1 = awaitTopicMessageOfType(c1, topicDest, "PROPERTY_BOUGHT", 4000);
                            Map<String, Object> bought2 = awaitTopicMessageOfType(c2, topicDest, "PROPERTY_BOUGHT", 4000);
                            assertEquals(bought1.get("payload"), bought2.get("payload"), "Both clients receive PROPERTY_BOUGHT");

                            snap1 = awaitGameSnapshot(c1, topicDest, 4000);
                            snap2 = awaitGameSnapshot(c2, topicDest, 4000);
                            assertSnapshotsConsistent(snap1, snap2);

                            Map<String, Object> updatedTile = findTileInSnapshot(snap1, pos);
                            assertEquals(curPlayerId, updatedTile.get("ownerId"), "Tile ownerId updated to buyer");
                            testedBuyProperty = true;
                        }

                        // End turn (handles EXTRA_ROLL if doubles!)
                        String prevPlayer = curPlayerId;
                        int prevTurn = curTurnId;
                        curClient.send("/app/room/" + roomId + "/end-turn", "{\"turnId\":" + curTurnId + "}");
                        Map<String, Object> tc1 = awaitTopicMessageOfType(c1, topicDest, "TURN_CHANGED", 4000);
                        Map<String, Object> tc2 = awaitTopicMessageOfType(c2, topicDest, "TURN_CHANGED", 4000);
                        assertEquals(tc1.get("payload"), tc2.get("payload"), "Both clients receive TURN_CHANGED");

                        snap1 = awaitGameSnapshot(c1, topicDest, 4000);
                        snap2 = awaitGameSnapshot(c2, topicDest, 4000);
                        assertSnapshotsConsistent(snap1, snap2);

                        Map<String, Object> tcPayload = MiniJson.parseObject((String) tc1.get("payload"));
                        if ("EXTRA_ROLL".equals(tcPayload.get("code"))) {
                            assertEquals(prevPlayer, snap1.get("currentPlayerId"), "Extra roll keeps same player");
                            assertEquals("AWAITING_ROLL", snap1.get("phase"), "Extra roll resets phase to AWAITING_ROLL");
                        } else {
                            assertEquals(prevTurn + 1, ((Number) snap1.get("turnId")).intValue(), "turnId incremented");
                            assertEquals("AWAITING_ROLL", snap1.get("phase"), "New turn starts AWAITING_ROLL");
                            testedEndTurn = true;
                        }
                    }
                }

                assertTrue(testedBuyProperty, "Successfully verified TILE_MISMATCH and PROPERTY_BOUGHT over STOMP");
                assertTrue(testedEndTurn, "Successfully verified end-turn advancement and snapshot sync");
                assertTrue(((Number) snap1.get("turnDeadlineEpochMs")).longValue() > 0L,
                        "Post-turn snapshot turnDeadlineEpochMs > 0");
                assertTrue(((Number) snap1.get("serverTimeEpochMs")).longValue() > 0L,
                        "Post-turn snapshot serverTimeEpochMs > 0");
                @SuppressWarnings("unchecked")
                List<Object> postLogs = (List<Object>) snap1.get("logs");
                assertTrue(postLogs != null && postLogs.size() >= 3,
                        "Snapshot logs accumulated across room actions");
            }
        }
    }

    private void testStompRoomLeaveTimeoutAndBotBroadcasts() throws Exception {
        try (GatewayHarness gw = startGatewayWithTurnTimeout("dev", null, 1)) {
            int port = gw.port;

            AuthUser p1 = registerViaRest(port, "botP1", "pass1234", "An Host");
            AuthUser p2 = registerViaRest(port, "botP2", "pass1234", "Bình Guest");
            AuthUser p3 = registerViaRest(port, "botP3", "pass1234", "Chi Visitor");
            Map<String, String> h1 = Map.of("Authorization", "Bearer " + p1.token);
            Map<String, String> h2 = Map.of("Authorization", "Bearer " + p2.token);
            Map<String, String> h3 = Map.of("Authorization", "Bearer " + p3.token);

            // Part 1: Verify ROOM_JOINED / ROOM_LEFT broadcasts + REST snapshot timer/logs
            HttpResponse createLobby = RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms", h1,
                    "{\"roomName\":\"Timer & Bot Arena\",\"maxPlayers\":3}");
            assertEquals(201, createLobby.statusCode, "Create room -> 201");
            String roomId = (String) MiniJson.parseObject((String) MiniJson.parseObject(createLobby.body).get("payload")).get("roomId");
            String topicDest = "/topic/room/" + roomId;

            try (StompWsClient c1 = new StompWsClient("127.0.0.1", port, "/ws", null)) {
                assertEquals("CONNECTED", c1.connectAndExpectFrame("Bearer " + p1.token, 4000).command, "c1 CONNECTED");
                c1.subscribe("sub-room-1", topicDest);
                Map<String, Object> initSnap = awaitGameSnapshot(c1, topicDest, 4000);
                assertEquals(0L, ((Number) initSnap.get("turnDeadlineEpochMs")).longValue(),
                        "WAITING room turnDeadlineEpochMs == 0");
                assertTrue(((Number) initSnap.get("serverTimeEpochMs")).longValue() > 0L,
                        "WAITING room serverTimeEpochMs > 0");

                // p2 & p3 join -> ROOM_JOINED broadcast
                assertEquals(200, RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms/" + roomId + "/join", h2, "{}").statusCode,
                        "p2 joins");
                Map<String, Object> join2Msg = awaitTopicMessageOfType(c1, topicDest, "ROOM_JOINED", 4000);
                assertEquals(p2.userId, join2Msg.get("senderId"), "ROOM_JOINED senderId matches p2");
                awaitGameSnapshot(c1, topicDest, 4000);

                assertEquals(200, RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms/" + roomId + "/join", h3, "{}").statusCode,
                        "p3 joins");
                awaitTopicMessageOfType(c1, topicDest, "ROOM_JOINED", 4000);
                awaitGameSnapshot(c1, topicDest, 4000);

                // p3 leaves -> ROOM_LEFT broadcast + snapshot log includes display name
                assertEquals(200, RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms/" + roomId + "/leave", h3, "{}").statusCode,
                        "p3 leaves");
                Map<String, Object> leftMsg = awaitTopicMessageOfType(c1, topicDest, "ROOM_LEFT", 4000);
                assertEquals(p3.userId, leftMsg.get("senderId"), "ROOM_LEFT senderId matches p3");
                Map<String, Object> afterLeaveSnap = awaitGameSnapshot(c1, topicDest, 4000);
                @SuppressWarnings("unchecked")
                List<Object> leaveLogs = (List<Object>) afterLeaveSnap.get("logs");
                assertTrue(leaveLogs.stream().anyMatch(l -> String.valueOf(l).contains("Chi Visitor đã rời phòng.")),
                        "Snapshot logs record player display name on leave");

                // Start game -> TurnTimerService 1s countdown starts -> Timeout & Bot takeover broadcasts
                assertEquals(200, RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms/" + roomId + "/start", h1, "{}").statusCode,
                        "p1 starts");
                awaitTopicMessageOfType(c1, topicDest, "GAME_STARTED", 4000);
                awaitGameSnapshot(c1, topicDest, 4000);

                // Verify REST snapshot restores turnDeadlineEpochMs > 0, serverTimeEpochMs > 0, and logs
                HttpResponse restSnapRes = RawHttpClient.request("127.0.0.1", port, "GET", "/api/v1/rooms/" + roomId + "/snapshot", h1, null);
                assertEquals(200, restSnapRes.statusCode, "GET snapshot -> 200");
                Map<String, Object> restSnap = MiniJson.parseObject((String) MiniJson.parseObject(restSnapRes.body).get("payload"));
                assertTrue(((Number) restSnap.get("turnDeadlineEpochMs")).longValue() > 0L,
                        "REST snapshot turnDeadlineEpochMs > 0");
                assertTrue(((Number) restSnap.get("serverTimeEpochMs")).longValue() > 0L,
                        "REST snapshot serverTimeEpochMs > 0");
                assertTrue(restSnap.get("logs") instanceof List<?> && !((List<?>) restSnap.get("logs")).isEmpty(),
                        "REST snapshot contains server logs");

                // Wait for 1s timeout + Bot turn broadcasts
                Map<String, Object> timeoutMsg = awaitTopicMessageOfType(c1, topicDest, "TURN_TIMED_OUT", 5000);
                Map<String, Object> timeoutPayload = MiniJson.parseObject((String) timeoutMsg.get("payload"));
                assertEquals("TIMEOUT", timeoutPayload.get("reason"), "TURN_TIMED_OUT reason TIMEOUT");

                Map<String, Object> takeoverMsg = awaitTopicMessageOfType(c1, topicDest, "BOT_TAKEOVER", 5000);
                Map<String, Object> takeoverPayload = MiniJson.parseObject((String) takeoverMsg.get("payload"));
                assertNotNull(takeoverPayload.get("playerId"), "BOT_TAKEOVER has playerId");

                Map<String, Object> botDiceMsg = awaitTopicMessageOfType(c1, topicDest, "DICE_ROLLED", 5000);
                Map<String, Object> botDicePayload = MiniJson.parseObject((String) botDiceMsg.get("payload"));
                assertEquals(Boolean.TRUE, botDicePayload.get("bot"), "Bot DICE_ROLLED has bot=true");
                assertEquals("BOT_ROLL", botDicePayload.get("code"), "Bot DICE_ROLLED has code=BOT_ROLL");

                Map<String, Object> turnChangedMsg = awaitTopicMessageOfType(c1, topicDest, "TURN_CHANGED", 5000);
                assertNotNull(turnChangedMsg.get("payload"), "Bot TURN_CHANGED received");

                // Verify latest snapshot after bot turn has fresh deadline & [Bot] log
                Map<String, Object> postBotSnap = awaitTopicMessageOfType(c1, topicDest, "GAME_STATE_SNAPSHOT", 5000);
                Map<String, Object> postBotPayload = MiniJson.parseObject((String) postBotSnap.get("payload"));
                // Drain TURN_TIMED_OUT / EXTRA_ROLL snapshots if consumed before final TURN_ENDED snapshot
                for (int i = 0; i < 6; i++) {
                    @SuppressWarnings("unchecked")
                    List<Object> curLogs = (List<Object>) postBotPayload.get("logs");
                    if (curLogs != null && curLogs.stream().anyMatch(l -> String.valueOf(l).contains("[Bot]"))) {
                        break;
                    }
                    Map<String, Object> nextSnap = awaitTopicMessageOfType(c1, topicDest, "GAME_STATE_SNAPSHOT", 3000);
                    postBotPayload = MiniJson.parseObject((String) nextSnap.get("payload"));
                }
                assertTrue(((Number) postBotPayload.get("turnDeadlineEpochMs")).longValue() > 0L,
                        "Post-bot snapshot turnDeadlineEpochMs > 0");
                @SuppressWarnings("unchecked")
                List<Object> botLogs = (List<Object>) postBotPayload.get("logs");
                assertTrue(botLogs.stream().anyMatch(l -> String.valueOf(l).contains("hết giờ lượt #")),
                        "Snapshot logs contain timeout entry");
                assertTrue(botLogs.stream().anyMatch(l -> String.valueOf(l).contains("[Bot]")),
                        "Snapshot logs contain [Bot] entry");
            }
        }
    }

    // =========================================================================
    // SUITE E: REGRESSION SUITE (GO, JAIL, BOT CONCURRENCY, UPGRADE, LEAVE, WS)
    // =========================================================================

    private void testSuiteE_GoLandingAndNegativeSteps() {
        GameRoom room = new GameRoom("suite-e-go", "GO Room", 2);
        Player p = new Player("e-go-1", "GO Tester", 1_500_000);

        // 35 + 5 -> 0: move() must NOT award $200,000; GoTile.onLand() awards $200,000 once.
        p.setPosition(35);
        p.move(5);
        assertEquals(0, p.getPosition(), "35 + 5 wraps to 0");
        assertEquals(1_500_000, p.getMoney(), "Landing directly on GO does not award $200,000 inside move()");
        new GoTile(0, "GO").onLand(p, room);
        assertEquals(1_700_000, p.getMoney(), "Total GO reward on 35+5->0 is $200,000 (not $400,000)");

        // 35 + 7 -> 2: passing GO strictly awards +$200,000 in move()
        p.setPosition(35);
        p.move(7);
        assertEquals(2, p.getPosition(), "35 + 7 wraps to 2");
        assertEquals(1_900_000, p.getMoney(), "Passing GO (35+7->2) awards +$200,000");

        // Negative steps must award $0
        p.setPosition(5);
        p.move(-10);
        assertEquals(35, p.getPosition(), "5 - 10 wraps to 35");
        assertEquals(1_900_000, p.getMoney(), "Negative steps award $0");

        // IN_JAIL player moving must not receive GO bonus
        p.sendToJail(10);
        p.move(35);
        assertEquals(1_900_000, p.getMoney(), "IN_JAIL move awards $0 GO bonus");
    }

    private void testSuiteE_BankruptcyNullResetAndUnmodifiableOwnedProps() {
        Player p = new Player("e-bk-1", "Bankrupt Tester", 1_500_000);
        StreetProperty s1 = new StreetProperty(1, "Phố Huế", 60_000, "BROWN", 10_000);
        StreetProperty s2 = new StreetProperty(3, "Hàng Bài", 60_000, "BROWN", 12_000);
        p.buyProperty(s1);
        p.buyProperty(s2);

        s1.setHouseCount(2);
        s1.setMortgaged(true);
        s2.setHasHotel(true);
        s2.setMortgaged(false);

        boolean threwUnsupported = false;
        try {
            p.getOwnedProperties().add(s1);
        } catch (UnsupportedOperationException expected) {
            threwUnsupported = true;
        }
        assertTrue(threwUnsupported, "Player.getOwnedProperties() must be unmodifiable");

        p.declareBankruptcy(null);
        assertEquals(PlayerStatus.BANKRUPT, p.getStatus(), "Player becomes BANKRUPT");
        assertTrue(p.getOwnedProperties().isEmpty(), "Owned properties cleared on bankruptcy");

        assertTrue(s1.getOwner() == null, "s1 owner cleared");
        assertFalse(s1.isMortgaged(), "s1 mortgaged flag reset");
        assertEquals(0, s1.getHouseCount(), "s1 houseCount reset to 0");
        assertFalse(s1.isHasHotel(), "s1 hasHotel reset to false");

        assertTrue(s2.getOwner() == null, "s2 owner cleared");
        assertFalse(s2.isMortgaged(), "s2 mortgaged flag reset");
        assertEquals(0, s2.getHouseCount(), "s2 houseCount reset to 0");
        assertFalse(s2.isHasHotel(), "s2 hasHotel reset to false");
    }

    private void testSuiteE_JailMechanicsAndPayBailReclaim() {
        // 1. Rolling in Jail for 3 turns -> auto-bail ($50,000) on 3rd turn
        GameRoom r1 = new GameRoom("e-jail-1", "Jail Roll Room", 2);
        Player p1 = new Player("j1", "Prisoner 1", 1_500_000);
        Player p2 = new Player("j2", "Guard 2", 1_500_000);
        r1.addPlayer(p1);
        r1.addPlayer(p2);
        GameEngine e1 = new GameEngine(r1);
        assertTrue(e1.startGame(), "Start r1");

        p1.sendToJail(10);
        for (int i = 1; i <= 2; i++) {
            assertEquals(p1, r1.getCurrentPlayer(), "p1 turn " + i);
            r1.setForcedDiceValue(3); // forced -> isDouble() == false
            assertTrue(e1.rollDice("e-jail-1", p1.getId(), r1.getTurnId(), "jr-" + i).isSuccess(), "p1 jail roll " + i);
            assertEquals(PlayerStatus.IN_JAIL, p1.getStatus(), "p1 stays IN_JAIL after roll " + i);

            assertEquals(p2, r1.getCurrentPlayer(), "p2 turn " + i);
            r1.setForcedDiceValue(3);
            assertTrue(e1.rollDice("e-jail-1", p2.getId(), r1.getTurnId(), "p2r-" + i).isSuccess(), "p2 roll " + i);
            assertTrue(e1.endTurn("e-jail-1", p2.getId(), r1.getTurnId(), "p2e-" + i).isSuccess(), "p2 end " + i);
        }
        assertEquals(p1, r1.getCurrentPlayer(), "p1 turn 3");
        r1.setForcedDiceValue(3); // lands on index 13 (StreetProperty, unowned)
        assertTrue(e1.rollDice("e-jail-1", p1.getId(), r1.getTurnId(), "jr-3").isSuccess(), "p1 jail roll 3");
        assertEquals(PlayerStatus.ACTIVE, p1.getStatus(), "p1 auto-bailed on 3rd jail roll");
        assertEquals(1_450_000, p1.getMoney(), "p1 paid $50,000 auto-bail on 3rd jail roll");

        // 2. Skipping 3 turns in Jail -> auto-bail ($50,000) on 3rd skip
        GameRoom r2 = new GameRoom("e-jail-2", "Jail Skip Room", 2);
        Player s1 = new Player("js1", "Skipper 1", 1_500_000);
        Player s2 = new Player("js2", "Other 2", 1_500_000);
        r2.addPlayer(s1);
        r2.addPlayer(s2);
        GameEngine e2 = new GameEngine(r2);
        assertTrue(e2.startGame(), "Start r2");

        s1.sendToJail(10);
        for (int i = 1; i <= 2; i++) {
            assertTrue(e2.endTurn("e-jail-2", s1.getId(), r2.getTurnId(), "skip-" + i).isSuccess(), "s1 skip " + i);
            assertEquals(PlayerStatus.IN_JAIL, s1.getStatus(), "s1 stays IN_JAIL after skip " + i);
            r2.setForcedDiceValue(3);
            assertTrue(e2.rollDice("e-jail-2", s2.getId(), r2.getTurnId(), "s2r-" + i).isSuccess(), "s2 roll " + i);
            assertTrue(e2.endTurn("e-jail-2", s2.getId(), r2.getTurnId(), "s2e-" + i).isSuccess(), "s2 end " + i);
        }
        assertTrue(e2.endTurn("e-jail-2", s1.getId(), r2.getTurnId(), "skip-3").isSuccess(), "s1 skip 3");
        assertEquals(PlayerStatus.ACTIVE, s1.getStatus(), "s1 auto-bailed on 3rd skip");
        assertEquals(1_450_000, s1.getMoney(), "s1 paid $50,000 auto-bail on 3rd skip");

        // 2b. Skipping 3 turns in Jail with < $50,000 -> BANKRUPT (s2 is current player after s1's 3rd skip)
        s2.deductMoney(s2.getMoney() - 20_000);
        s2.sendToJail(10);
        for (int i = 1; i <= 2; i++) {
            assertTrue(e2.endTurn("e-jail-2", s2.getId(), r2.getTurnId(), "s2skip-" + i).isSuccess(), "s2 skip " + i);
            r2.setForcedDiceValue(3);
            assertTrue(e2.rollDice("e-jail-2", s1.getId(), r2.getTurnId(), "s1r-" + i).isSuccess(), "s1 roll " + i);
            assertTrue(e2.endTurn("e-jail-2", s1.getId(), r2.getTurnId(), "s1e-" + i).isSuccess(), "s1 end " + i);
        }
        assertTrue(e2.endTurn("e-jail-2", s2.getId(), r2.getTurnId(), "s2skip-3").isSuccess(), "s2 skip 3");
        assertEquals(PlayerStatus.BANKRUPT, s2.getStatus(), "s2 bankrupt when unable to pay 3rd-turn auto-bail");
        assertEquals(RoomStatus.FINISHED, r2.getStatus(), "r2 finished after s2 bankruptcy");

        // 3. Bot in Jail escapes/advances without getting stuck
        GameRoom r3 = new GameRoom("e-jail-3", "Bot Jail Room", 2);
        Player b1 = new Player("jb1", "Bot Prisoner", 1_500_000);
        Player b2 = new Player("jb2", "Human Peer", 1_500_000);
        b1.setBot(true);
        r3.addPlayer(b1);
        r3.addPlayer(b2);
        GameEngine e3 = new GameEngine(r3);
        assertTrue(e3.startGame(), "Start r3");
        b1.sendToJail(10);
        int turnBeforeBot = r3.getTurnId();
        BotController.processBotTurn(e3);
        assertTrue(r3.getTurnId() > turnBeforeBot, "Bot in Jail advances turn instead of getting stuck");
        assertEquals(b2, r3.getCurrentPlayer(), "Turn passes to b2 after bot jail turn");

        // 4. payBail reclaims human control from TIMEOUT
        GameRoom r4 = new GameRoom("e-jail-4", "Bail Timeout Room", 2);
        Player h1 = new Player("jh1", "Timed Out Prisoner", 1_500_000);
        Player h2 = new Player("jh2", "Peer", 1_500_000);
        r4.addPlayer(h1);
        r4.addPlayer(h2);
        GameEngine e4 = new GameEngine(r4);
        assertTrue(e4.startGame(), "Start r4");
        h1.sendToJail(10);
        int tid = r4.getTurnId();
        assertTrue(e4.handleTimeout("e-jail-4", h1.getId(), tid, "to-jail").isSuccess(), "Timeout triggered");
        assertTrue(e4.isBotTurnActive("e-jail-4", h1.getId(), tid), "Bot control active during TIMEOUT");
        ActionResult bailRes = e4.payBail("e-jail-4", h1.getId(), tid, "bail-reclaim");
        assertTrue(bailRes.isSuccess(), "payBail succeeds during TIMEOUT");
        assertFalse(e4.isBotTurnActive("e-jail-4", h1.getId(), tid), "payBail reclaims human control from TIMEOUT");
        assertEquals(PlayerStatus.ACTIVE, h1.getStatus(), "h1 is ACTIVE after payBail");
        assertEquals(1_450_000, h1.getMoney(), "h1 paid $50,000 bail");
    }

    private void testSuiteE_ConcurrentBotAndHumanNoDeadlock() throws Exception {
        GameRoom room = new GameRoom("e-conc", "Concurrency Room", 2);
        Player bot = new Player("c-bot", "Bot", 1_500_000);
        bot.setBot(true);
        Player human = new Player("c-hum", "Human", 1_500_000);
        room.addPlayer(bot);
        room.addPlayer(human);
        GameEngine engine = new GameEngine(room);
        assertTrue(engine.startGame(), "Start concurrency room");

        List<Throwable> errors = new CopyOnWriteArrayList<>();
        Thread botThread = new Thread(() -> {
            try {
                for (int i = 0; i < 120 && room.getStatus() == RoomStatus.PLAYING; i++) {
                    BotController.processBotTurn(engine);
                    Thread.yield();
                }
            } catch (Throwable t) {
                errors.add(t);
            }
        }, "test-bot-thread");

        Thread humanThread = new Thread(() -> {
            try {
                for (int i = 0; i < 120 && room.getStatus() == RoomStatus.PLAYING; i++) {
                    int tid = room.getTurnId();
                    engine.rollDice("e-conc", human.getId(), tid, "hr-" + i);
                    engine.buyProperty("e-conc", human.getId(), tid, "hb-" + i);
                    engine.endTurn("e-conc", human.getId(), tid, "he-" + i);
                    // Concurrent snapshot-style reads on room/board
                    room.getPlayers();
                    room.getCurrentPlayer();
                    room.getBoard().getTiles();
                    Thread.yield();
                }
            } catch (Throwable t) {
                errors.add(t);
            }
        }, "test-human-thread");

        botThread.start();
        humanThread.start();
        botThread.join(5000);
        humanThread.join(5000);

        assertFalse(botThread.isAlive(), "Bot thread finished without deadlock");
        assertFalse(humanThread.isAlive(), "Human thread finished without deadlock");
        assertTrue(errors.isEmpty(), "No exceptions during concurrent Bot + human execution");
    }

    private void testSuiteE_StompUpgradeProperty() throws Exception {
        try (GatewayHarness gw = startGateway("dev", null)) {
            int port = gw.port;
            AuthUser p1 = registerViaRest(port, "upgP1", "pass1234", "Upgrade Host");
            AuthUser p2 = registerViaRest(port, "upgP2", "pass1234", "Upgrade Guest");
            Map<String, String> h1 = Map.of("Authorization", "Bearer " + p1.token);
            Map<String, String> h2 = Map.of("Authorization", "Bearer " + p2.token);

            HttpResponse createRes = RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms", h1,
                    "{\"roomName\":\"Upgrade Arena\",\"maxPlayers\":2}");
            assertEquals(201, createRes.statusCode, "Create room -> 201");
            String roomId = (String) MiniJson.parseObject((String) MiniJson.parseObject(createRes.body).get("payload")).get("roomId");
            assertEquals(200, RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms/" + roomId + "/join", h2, "{}").statusCode,
                    "p2 joins");

            String topicDest = "/topic/room/" + roomId;
            try (StompWsClient c1 = new StompWsClient("127.0.0.1", port, "/ws", null);
                 StompWsClient c2 = new StompWsClient("127.0.0.1", port, "/ws", null)) {

                assertEquals("CONNECTED", c1.connectAndExpectFrame("Bearer " + p1.token, 4000).command, "c1 CONNECTED");
                assertEquals("CONNECTED", c2.connectAndExpectFrame("Bearer " + p2.token, 4000).command, "c2 CONNECTED");
                c1.subscribe("sub-1", topicDest);
                c2.subscribe("sub-2", topicDest);
                awaitGameSnapshot(c1, topicDest, 4000);
                awaitGameSnapshot(c2, topicDest, 4000);

                assertEquals(200, RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms/" + roomId + "/start", h1, "{}").statusCode,
                        "Start -> 200");
                awaitTopicMessageOfType(c1, topicDest, "GAME_STARTED", 4000);
                Map<String, Object> snap = awaitGameSnapshot(c1, topicDest, 4000);

                boolean upgraded = false;
                for (int step = 0; step < 16 && !upgraded; step++) {
                    String curId = (String) snap.get("currentPlayerId");
                    int tid = ((Number) snap.get("turnId")).intValue();
                    StompWsClient activeClient = p1.userId.equals(curId) ? c1 : c2;

                    activeClient.send("/app/room/" + roomId + "/roll-dice", "{\"turnId\":" + tid + "}");
                    snap = awaitGameSnapshot(c1, topicDest, 4000);
                    if (!"AWAITING_DECISION".equals(snap.get("phase"))) {
                        continue;
                    }

                    int pos = findPlayerPosition(snap, curId);
                    Map<String, Object> tile = findTile(snap, pos);
                    if ("STREET".equals(tile.get("type")) && tile.get("ownerId") == null) {
                        activeClient.send("/app/room/" + roomId + "/buy-property",
                                "{\"turnId\":" + tid + ",\"tileIndex\":" + pos + "}");
                        awaitTopicMessageOfType(c1, topicDest, "PROPERTY_BOUGHT", 4000);
                        snap = awaitGameSnapshot(c1, topicDest, 4000);

                        activeClient.send("/app/room/" + roomId + "/upgrade-property",
                                "{\"turnId\":" + tid + ",\"tileIndex\":" + pos + "}");
                        Map<String, Object> upgMsg = awaitTopicMessageOfType(c1, topicDest, "PROPERTY_UPGRADED", 4000);
                        Map<String, Object> upgPayload = MiniJson.parseObject((String) upgMsg.get("payload"));
                        assertEquals(1, ((Number) upgPayload.get("houseCount")).intValue(), "PROPERTY_UPGRADED houseCount == 1");

                        snap = awaitGameSnapshot(c1, topicDest, 4000);
                        Map<String, Object> upgradedTile = findTile(snap, pos);
                        assertEquals(1, ((Number) upgradedTile.get("houseCount")).intValue(),
                                "Snapshot tile houseCount == 1 after upgrade");
                        upgraded = true;
                        break;
                    }

                    activeClient.send("/app/room/" + roomId + "/end-turn", "{\"turnId\":" + tid + "}");
                    snap = awaitGameSnapshot(c1, topicDest, 4000);
                }
                assertTrue(upgraded, "Successfully landed on, bought, and upgraded a StreetProperty via STOMP");
            }
        }
    }

    private void testSuiteE_MidGameLeaveSurrenderAndFinish() throws Exception {
        try (GatewayHarness gw = startGateway("dev", null)) {
            int port = gw.port;
            AuthUser p1 = registerViaRest(port, "lvP1", "pass1234", "Leave Host");
            AuthUser p2 = registerViaRest(port, "lvP2", "pass1234", "Leave Guest");
            Map<String, String> h1 = Map.of("Authorization", "Bearer " + p1.token);
            Map<String, String> h2 = Map.of("Authorization", "Bearer " + p2.token);

            HttpResponse createRes = RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms", h1,
                    "{\"roomName\":\"Surrender Arena\",\"maxPlayers\":2}");
            assertEquals(201, createRes.statusCode, "Create room -> 201");
            String roomId = (String) MiniJson.parseObject((String) MiniJson.parseObject(createRes.body).get("payload")).get("roomId");
            assertEquals(200, RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms/" + roomId + "/join", h2, "{}").statusCode,
                    "p2 joins");

            String topicDest = "/topic/room/" + roomId;
            try (StompWsClient c1 = new StompWsClient("127.0.0.1", port, "/ws", null)) {
                assertEquals("CONNECTED", c1.connectAndExpectFrame("Bearer " + p1.token, 4000).command, "c1 CONNECTED");
                c1.subscribe("sub-1", topicDest);
                awaitGameSnapshot(c1, topicDest, 4000);

                assertEquals(200, RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms/" + roomId + "/start", h1, "{}").statusCode,
                        "Start -> 200");
                awaitTopicMessageOfType(c1, topicDest, "GAME_STARTED", 4000);
                Map<String, Object> snap = awaitGameSnapshot(c1, topicDest, 4000);

                // Have p1 buy a property if possible before surrendering
                Integer boughtTileIdx = null;
                if (p1.userId.equals(snap.get("currentPlayerId"))) {
                    int tid = ((Number) snap.get("turnId")).intValue();
                    c1.send("/app/room/" + roomId + "/roll-dice", "{\"turnId\":" + tid + "}");
                    snap = awaitGameSnapshot(c1, topicDest, 4000);
                    if ("AWAITING_DECISION".equals(snap.get("phase"))) {
                        int pos = findPlayerPosition(snap, p1.userId);
                        Map<String, Object> tile = findTile(snap, pos);
                        if (tile.get("ownerId") == null && tile.get("price") instanceof Number
                                && ((Number) tile.get("price")).intValue() > 0) {
                            c1.send("/app/room/" + roomId + "/buy-property",
                                    "{\"turnId\":" + tid + ",\"tileIndex\":" + pos + "}");
                            awaitTopicMessageOfType(c1, topicDest, "PROPERTY_BOUGHT", 4000);
                            awaitGameSnapshot(c1, topicDest, 4000);
                            boughtTileIdx = pos;
                        }
                    }
                }

                // Mid-game POST /leave by p1 surrenders p1 and finishes the 2-player match
                HttpResponse leaveRes = RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms/" + roomId + "/leave", h1, "{}");
                assertEquals(200, leaveRes.statusCode, "Mid-game leave -> 200");

                awaitTopicMessageOfType(c1, topicDest, "GAME_FINISHED", 4000);

                HttpResponse finalSnapRes = RawHttpClient.request("127.0.0.1", port, "GET", "/api/v1/rooms/" + roomId + "/snapshot", h2, null);
                assertEquals(200, finalSnapRes.statusCode, "Final snapshot -> 200");
                Map<String, Object> finalSnap = MiniJson.parseObject((String) MiniJson.parseObject(finalSnapRes.body).get("payload"));
                assertEquals("FINISHED", finalSnap.get("roomStatus"), "Room status is FINISHED after 2-player surrender");

                if (boughtTileIdx != null) {
                    Map<String, Object> clearedTile = findTile(finalSnap, boughtTileIdx);
                    assertTrue(clearedTile.get("ownerId") == null, "Surrendered player's property ownership cleared");
                }
            }
        }
    }

    private void testSuiteE_StompHeartbeatReconnectAuthAndProdJwt() throws Exception {
        // 1. PROD GatewayConfig rejects missing or default JWT_SECRET
        boolean threwMissingJwt = false;
        try {
            GatewayConfig.fromArgsAndEnv(new String[]{"--mode=prod", "--secret=prod-origin-secret"}, Map.of(), null);
        } catch (IllegalArgumentException expected) {
            threwMissingJwt = true;
        }
        assertTrue(threwMissingJwt, "PROD GatewayConfig rejects missing JWT_SECRET");

        boolean threwDefaultJwt = false;
        try {
            GatewayConfig.fromArgsAndEnv(
                    new String[]{"--mode=prod", "--secret=prod-origin-secret", "--jwt-secret=" + GatewayConfig.DEFAULT_JWT_SECRET},
                    Map.of(),
                    null);
        } catch (IllegalArgumentException expected) {
            threwDefaultJwt = true;
        }
        assertTrue(threwDefaultJwt, "PROD GatewayConfig rejects default JWT_SECRET");

        GatewayConfig validProd = GatewayConfig.fromArgsAndEnv(
                new String[]{"--mode=prod", "--secret=prod-origin-secret", "--jwt-secret=CustomProdJwtSecretKey2026Strong"},
                Map.of(),
                null);
        assertEquals("prod", validProd.getMode(), "Valid PROD GatewayConfig accepted");

        // 2. STOMP Heartbeat negotiation, non-member subscribe ERROR, and WS disconnect/reconnect snapshot recovery
        try (GatewayHarness gw = startGateway("dev", null)) {
            int port = gw.port;
            AuthUser p1 = registerViaRest(port, "hbP1", "pass1234", "HB Host");
            AuthUser p2 = registerViaRest(port, "hbP2", "pass1234", "HB Guest");
            AuthUser p3 = registerViaRest(port, "hbP3", "pass1234", "HB Outsider");
            Map<String, String> h1 = Map.of("Authorization", "Bearer " + p1.token);
            Map<String, String> h2 = Map.of("Authorization", "Bearer " + p2.token);

            HttpResponse createRes = RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms", h1,
                    "{\"roomName\":\"WS Resilience Arena\",\"maxPlayers\":2}");
            assertEquals(201, createRes.statusCode, "Create room -> 201");
            String roomId = (String) MiniJson.parseObject((String) MiniJson.parseObject(createRes.body).get("payload")).get("roomId");
            assertEquals(200, RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms/" + roomId + "/join", h2, "{}").statusCode,
                    "p2 joins");
            assertEquals(200, RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms/" + roomId + "/start", h1, "{}").statusCode,
                    "Start -> 200");

            String topicDest = "/topic/room/" + roomId;

            // Non-member subscribe rejected with STOMP ERROR
            try (StompWsClient outsider = new StompWsClient("127.0.0.1", port, "/ws", null)) {
                StompFrame conn = outsider.connectAndExpectFrame("Bearer " + p3.token, "10000,10000", 4000);
                assertEquals("CONNECTED", conn.command, "Outsider CONNECTED");
                String hb = conn.headers.get("heart-beat");
                assertNotNull(hb, "CONNECTED includes heart-beat header");
                assertFalse("0,0".equals(hb), "Negotiated heart-beat is non-zero for 10000,10000: " + hb);

                outsider.subscribe("sub-outsider", topicDest);
                StompFrame err = outsider.awaitAndConsume(f -> "ERROR".equals(f.command), 4000);
                assertNotNull(err, "Non-member subscribe receives STOMP ERROR");
                assertTrue(err.body != null && err.body.contains("FORBIDDEN_SUBSCRIPTION"),
                        "STOMP ERROR body indicates FORBIDDEN_SUBSCRIPTION");
            }

            // Mid-game WS disconnect & reconnect with snapshot recovery
            int initialTurnId;
            try (StompWsClient c1 = new StompWsClient("127.0.0.1", port, "/ws", null)) {
                StompFrame conn1 = c1.connectAndExpectFrame("Bearer " + p1.token, "10000,10000", 4000);
                assertEquals("CONNECTED", conn1.command, "c1 CONNECTED");
                c1.subscribe("sub-1", topicDest);
                Map<String, Object> snap1 = awaitGameSnapshot(c1, topicDest, 4000);
                initialTurnId = ((Number) snap1.get("turnId")).intValue();
            } // c1 disconnects here

            try (StompWsClient c1Reconnect = new StompWsClient("127.0.0.1", port, "/ws", null)) {
                StompFrame conn2 = c1Reconnect.connectAndExpectFrame("Bearer " + p1.token, "10000,10000", 4000);
                assertEquals("CONNECTED", conn2.command, "c1Reconnect CONNECTED");
                c1Reconnect.subscribe("sub-rec", topicDest);
                Map<String, Object> recoveredSnap = awaitGameSnapshot(c1Reconnect, topicDest, 4000);
                assertEquals(roomId, recoveredSnap.get("roomId"), "Reconnected snapshot roomId matches");
                assertEquals(initialTurnId, ((Number) recoveredSnap.get("turnId")).intValue(),
                        "Reconnected snapshot turnId matches");
                assertEquals("PLAYING", recoveredSnap.get("roomStatus"), "Reconnected snapshot status is PLAYING");
            }
        }
    }

    private void testSuiteE_TimeoutInAwaitingDecisionAndEnsureBotResolved() throws Exception {
        // 1. Engine-level: timeout in AWAITING_DECISION (unowned property & doubles) + ensureBotTurnResolved
        GameRoom room = new GameRoom("e-to-dec", "Timeout Decision Room", 2);
        Player p1 = new Player("td1", "Human 1", 1_500_000);
        Player p2 = new Player("td2", "Human 2", 1_500_000);
        room.addPlayer(p1);
        room.addPlayer(p2);
        GameEngine engine = new GameEngine(room);
        assertTrue(engine.startGame(), "Start timeout-decision room");

        // Land on unowned property (index 3) -> AWAITING_DECISION
        room.setForcedDiceValue(3);
        int turn1 = room.getTurnId();
        assertTrue(engine.rollDice("e-to-dec", p1.getId(), turn1, "td-r1").isSuccess(), "Roll into AWAITING_DECISION");
        assertEquals("AWAITING_DECISION", engine.getEffectivePhase().name(),
                "Effective phase is AWAITING_DECISION");

        // Timeout while in AWAITING_DECISION
        assertTrue(engine.handleTimeout("e-to-dec", p1.getId(), turn1, "td-to1").isSuccess(),
                "Timeout succeeds in AWAITING_DECISION");
        assertEquals("TIMEOUT", engine.getCurrentPhase().name(), "Current phase is TIMEOUT");
        assertTrue(engine.isBotControlActive(), "Bot control active after timeout");
        assertEquals("AWAITING_DECISION", engine.getEffectivePhase().name(),
                "getEffectivePhase() preserves AWAITING_DECISION (never TIMEOUT)");

        BotController.processBotTurn(engine);
        assertEquals(p2, room.getCurrentPlayer(), "Bot completes decision and advances turn to p2");
        assertFalse(engine.isBotControlActive(), "Bot control cleared after turn advance");

        // Trigger timeout on p2 and verify ensureBotTurnResolved recovers stuck turn
        int turn2 = room.getTurnId();
        assertTrue(engine.handleTimeout("e-to-dec", p2.getId(), turn2, "td-to2").isSuccess(), "Timeout on p2");
        assertTrue(engine.ensureBotTurnResolved("e-to-dec", turn2), "ensureBotTurnResolved recovers stuck turn");
        assertEquals(p1, room.getCurrentPlayer(), "Turn recovered back to p1");
        assertFalse(engine.isBotControlActive(), "Bot control cleared after ensureBotTurnResolved");

        // 2. Live Gateway + STOMP: timeout during AWAITING_DECISION reports valid actionPhase (never TIMEOUT)
        try (GatewayHarness gw = startGatewayWithTurnTimeout("dev", null, 1)) {
            int port = gw.port;
            AuthUser u1 = registerViaRest(port, "tdWs1", "pass1234", "TD Host");
            AuthUser u2 = registerViaRest(port, "tdWs2", "pass1234", "TD Guest");
            Map<String, String> h1 = Map.of("Authorization", "Bearer " + u1.token);
            Map<String, String> h2 = Map.of("Authorization", "Bearer " + u2.token);

            HttpResponse createRes = RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms", h1,
                    "{\"roomName\":\"TD WS Arena\",\"maxPlayers\":2}");
            assertEquals(201, createRes.statusCode, "Create room -> 201");
            String roomId = (String) MiniJson.parseObject((String) MiniJson.parseObject(createRes.body).get("payload")).get("roomId");
            assertEquals(200, RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms/" + roomId + "/join", h2, "{}").statusCode,
                    "u2 joins");

            String topicDest = "/topic/room/" + roomId;
            try (StompWsClient c1 = new StompWsClient("127.0.0.1", port, "/ws", null)) {
                assertEquals("CONNECTED", c1.connectAndExpectFrame("Bearer " + u1.token, 4000).command, "c1 CONNECTED");
                c1.subscribe("sub-1", topicDest);
                awaitGameSnapshot(c1, topicDest, 4000);

                assertEquals(200, RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms/" + roomId + "/start", h1, "{}").statusCode,
                        "Start -> 200");
                awaitTopicMessageOfType(c1, topicDest, "GAME_STARTED", 4000);
                Map<String, Object> snap = awaitGameSnapshot(c1, topicDest, 4000);

                // Roll until we reach AWAITING_DECISION (or timeout triggers on AWAITING_ROLL and advances)
                boolean testedDecisionTimeout = false;
                for (int i = 0; i < 6 && !testedDecisionTimeout; i++) {
                    String curId = (String) snap.get("currentPlayerId");
                    int tid = ((Number) snap.get("turnId")).intValue();
                    if (u1.userId.equals(curId)) {
                        c1.send("/app/room/" + roomId + "/roll-dice", "{\"turnId\":" + tid + "}");
                        snap = awaitGameSnapshot(c1, topicDest, 4000);
                    }
                    if ("AWAITING_DECISION".equals(snap.get("actionPhase"))) {
                        int decisionTid = ((Number) snap.get("turnId")).intValue();
                        // Let 1s timer expire in AWAITING_DECISION
                        Map<String, Object> toMsg = awaitTopicMessageOfType(c1, topicDest, "TURN_TIMED_OUT", 5000);
                        Map<String, Object> toPayload = MiniJson.parseObject((String) toMsg.get("payload"));
                        assertNotNull(toPayload.get("actionPhase"), "TURN_TIMED_OUT includes actionPhase");
                        assertFalse("TIMEOUT".equals(toPayload.get("actionPhase")),
                                "TURN_TIMED_OUT actionPhase is never TIMEOUT");

                        Map<String, Object> tcMsg = awaitTopicMessageOfType(c1, topicDest, "TURN_CHANGED", 5000);
                        Map<String, Object> tcPayload = MiniJson.parseObject((String) tcMsg.get("payload"));
                        assertTrue(((Number) tcPayload.get("newTurnId")).intValue() > decisionTid,
                                "Bot advances turn after AWAITING_DECISION timeout");
                        assertFalse("TIMEOUT".equals(tcPayload.get("phase")),
                                "TURN_CHANGED phase is never TIMEOUT");

                        Map<String, Object> postSnap = awaitTopicMessageOfType(c1, topicDest, "GAME_STATE_SNAPSHOT", 5000);
                        Map<String, Object> postPayload = MiniJson.parseObject((String) postSnap.get("payload"));
                        assertFalse("TIMEOUT".equals(postPayload.get("actionPhase")),
                                "Snapshot actionPhase is never TIMEOUT");
                        testedDecisionTimeout = true;
                        break;
                    } else {
                        // Wait for turn to advance
                        awaitTopicMessageOfType(c1, topicDest, "TURN_CHANGED", 5000);
                        snap = awaitGameSnapshot(c1, topicDest, 5000);
                    }
                }
                assertTrue(testedDecisionTimeout, "Verified live STOMP timeout during AWAITING_DECISION");
            }
        }
    }

    private void testSuiteE_DedicatedBotAutoPlaysWithoutWaiting30s() throws Exception {
        GameRoom room = new GameRoom("e-ded-bot", "Dedicated Bot Room", 2);
        Player human = new Player("db-hum", "Human", 1_500_000);
        Player bot = new Player("db-bot", "AI Bot", 1_500_000);
        bot.setBot(true);
        room.addPlayer(human);
        room.addPlayer(bot);

        GameEngine engine = new GameEngine(room);
        assertTrue(engine.startGame(), "Start dedicated bot room");

        List<String> eventTypes = new CopyOnWriteArrayList<>();
        CountDownLatch botTurnDone = new CountDownLatch(1);
        engine.addEventListener(evt -> {
            eventTypes.add(evt.getType().name());
            if (evt.getType() == EngineEvent.Type.TURN_ENDED && bot.getId().equals(evt.getPlayerId())) {
                botTurnDone.countDown();
            }
        });

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "test-dedicated-bot-timer");
            t.setDaemon(true);
            return t;
        });
        try {
            TurnTimerService timerService = new TurnTimerService(scheduler, 30);

            // Human plays turn 1 and ends turn -> now it is bot's turn
            int t1 = room.getTurnId();
            room.setForcedDiceValue(3);
            assertTrue(engine.rollDice("e-ded-bot", human.getId(), t1, "h-r1").isSuccess(), "Human rolls");
            assertTrue(engine.endTurn("e-ded-bot", human.getId(), t1, "h-e1").isSuccess(), "Human ends turn");
            assertEquals(bot, room.getCurrentPlayer(), "Now bot's turn");

            long startMs = System.currentTimeMillis();
            timerService.startTurnTimer("e-ded-bot", room.getTurnId(), engine);
            boolean completed = botTurnDone.await(5, TimeUnit.SECONDS);
            long elapsedMs = System.currentTimeMillis() - startMs;

            assertTrue(completed, "Dedicated bot auto-played without waiting 30s (elapsed=" + elapsedMs + "ms)");
            assertTrue(elapsedMs < 5000, "Dedicated bot completed in < 5s despite 30s timeout");
            assertFalse(eventTypes.contains("TURN_TIMED_OUT"), "Dedicated bot did not trigger TURN_TIMED_OUT");
            assertFalse(eventTypes.contains("BOT_TAKEOVER"), "Dedicated bot did not trigger BOT_TAKEOVER");
            assertEquals(human, room.getCurrentPlayer(), "Turn returned to human after dedicated bot turn");
        } finally {
            scheduler.shutdownNow();
        }
    }

    @SuppressWarnings("unchecked")
    private void testSuiteE_TimelineRentTableAndBotPositionSync() throws Exception {
        try (GatewayHarness gw = startGatewayWithTurnTimeout("dev", null, 1)) {
            int port = gw.port;
            AuthUser p1 = registerViaRest(port, "tlP1", "pass1234", "TL Host");
            AuthUser p2 = registerViaRest(port, "tlP2", "pass1234", "TL Guest");
            Map<String, String> h1 = Map.of("Authorization", "Bearer " + p1.token);
            Map<String, String> h2 = Map.of("Authorization", "Bearer " + p2.token);

            HttpResponse createRes = RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms", h1,
                    "{\"roomName\":\"Timeline Arena\",\"maxPlayers\":2}");
            assertEquals(201, createRes.statusCode, "Create room -> 201");
            String roomId = (String) MiniJson.parseObject((String) MiniJson.parseObject(createRes.body).get("payload")).get("roomId");
            String topicDest = "/topic/room/" + roomId;

            try (StompWsClient c1 = new StompWsClient("127.0.0.1", port, "/ws", null)) {
                assertEquals("CONNECTED", c1.connectAndExpectFrame("Bearer " + p1.token, 4000).command, "c1 CONNECTED");
                c1.subscribe("sub-1", topicDest);
                awaitGameSnapshot(c1, topicDest, 4000);

                assertEquals(200, RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms/" + roomId + "/join", h2, "{}").statusCode,
                        "p2 joins");
                assertEquals(200, RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms/" + roomId + "/start", h1, "{}").statusCode,
                        "Start -> 200");
                awaitTopicMessageOfType(c1, topicDest, "GAME_STARTED", 4000);

                // Wait for 1s timeout -> Bot takes over and rolls
                Map<String, Object> botDiceMsg = awaitTopicMessageOfType(c1, topicDest, "DICE_ROLLED", 5000);
                Map<String, Object> botDice = MiniJson.parseObject((String) botDiceMsg.get("payload"));
                assertEquals(Boolean.TRUE, botDice.get("bot"), "Bot DICE_ROLLED has bot=true");
                assertNotNull(botDice.get("fromPosition"), "DICE_ROLLED has fromPosition");
                assertNotNull(botDice.get("newPosition"), "DICE_ROLLED has newPosition");
                assertNotNull(botDice.get("finalPosition"), "DICE_ROLLED has finalPosition");
                int newPos = ((Number) botDice.get("newPosition")).intValue();

                awaitTopicMessageOfType(c1, topicDest, "TURN_CHANGED", 5000);

                // Verify TIMELINE_APPENDED broadcasts
                List<Map<String, Object>> appended = new ArrayList<>();
                for (int i = 0; i < 6; i++) {
                    Map<String, Object> msg = awaitTopicMessageOfTypeOrNull(c1, topicDest, "TIMELINE_APPENDED", 400);
                    if (msg == null) break;
                    appended.add(MiniJson.parseObject((String) msg.get("payload")));
                }
                assertTrue(appended.size() >= 3, "Received TIMELINE_APPENDED broadcasts (got " + appended.size() + ")");
                long prevId = -1;
                long prevTs = -1;
                for (Map<String, Object> entry : appended) {
                    long id = ((Number) entry.get("id")).longValue();
                    long ts = ((Number) entry.get("ts")).longValue();
                    assertTrue(id > prevId, "TIMELINE_APPENDED id strictly increasing");
                    assertTrue(ts >= prevTs, "TIMELINE_APPENDED ts monotonic");
                    prevId = id;
                    prevTs = ts;
                }

                // Verify REST snapshot timeline, Asia/Ho_Chi_Minh legacy logs, Bot dice log & rentTable
                HttpResponse snapRes = RawHttpClient.request("127.0.0.1", port, "GET", "/api/v1/rooms/" + roomId + "/snapshot", h1, null);
                assertEquals(200, snapRes.statusCode, "GET snapshot -> 200");
                Map<String, Object> snap = MiniJson.parseObject((String) MiniJson.parseObject(snapRes.body).get("payload"));

                List<Object> timeline = (List<Object>) snap.get("timeline");
                assertNotNull(timeline, "Snapshot includes timeline");
                assertTrue(timeline.size() >= 4, "Timeline has >= 4 entries");
                long tlPrevId = Long.MAX_VALUE;
                long tlPrevTs = Long.MAX_VALUE;
                int botRollLogCount = 0;
                int takeoverLogCount = 0;
                for (Object obj : timeline) {
                    Map<String, Object> e = (Map<String, Object>) obj;
                    long id = ((Number) e.get("id")).longValue();
                    long ts = ((Number) e.get("ts")).longValue();
                    String msg = String.valueOf(e.get("message"));
                    assertTrue(id < tlPrevId, "Snapshot timeline sorted newest-first (strictly decreasing id)");
                    assertTrue(ts <= tlPrevTs, "Snapshot timeline ts monotonic");
                    tlPrevId = id;
                    tlPrevTs = ts;
                    if (msg.contains("gieo được") && msg.contains("-> Ô #" + newPos)) {
                        botRollLogCount++;
                        assertTrue(msg.startsWith("[Bot] "), "Bot roll log has [Bot] prefix: " + msg);
                    }
                    if (msg.startsWith("[Bot] Tiếp quản lượt")) {
                        takeoverLogCount++;
                    }
                }
                assertTrue(botRollLogCount >= 1, "Bot roll log matches newPosition Ô #" + newPos);
                assertEquals(1, takeoverLogCount, "Exactly 1 canonical BOT_TAKEOVER log for turn 1");

                // Verify legacy logs have [HH:mm:ss] in Asia/Ho_Chi_Minh
                List<Object> logs = (List<Object>) snap.get("logs");
                assertNotNull(logs, "Snapshot includes legacy logs");
                String vnHour = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:"));
                assertTrue(String.valueOf(logs.get(0)).startsWith("[" + vnHour),
                        "Legacy logs formatted in Asia/Ho_Chi_Minh (" + logs.get(0) + ")");

                // Verify snapshot rentTable & taxAmount
                Map<String, Object> street1 = findTile(snap, 1);
                List<Object> streetRentTable = (List<Object>) street1.get("rentTable");
                assertNotNull(streetRentTable, "Street tile has rentTable");
                assertEquals(5, streetRentTable.size(), "Street rentTable has 5 entries");
                assertEquals(50_000, ((Number) street1.get("buildingCost")).intValue(), "Street 1 buildingCost == 50,000");

                Map<String, Object> rr5 = findTile(snap, 5);
                List<Object> rrTable = (List<Object>) rr5.get("rentTable");
                assertEquals(List.of(25_000L, 50_000L, 100_000L, 200_000L),
                        rrTable.stream().map(n -> ((Number) n).longValue()).toList(),
                        "Railroad rentTable == [25000, 50000, 100000, 200000]");

                Map<String, Object> util12 = findTile(snap, 12);
                List<Object> utilTable = (List<Object>) util12.get("rentTable");
                assertEquals(List.of(4_000L, 10_000L),
                        utilTable.stream().map(n -> ((Number) n).longValue()).toList(),
                        "Utility rentTable == [4000, 10000]");

                Map<String, Object> tax4 = findTile(snap, 4);
                assertEquals(200_000, ((Number) tax4.get("taxAmount")).intValue(), "Income Tax == 200,000");
                Map<String, Object> tax38 = findTile(snap, 38);
                assertEquals(100_000, ((Number) tax38.get("taxAmount")).intValue(), "Luxury Tax == 100,000");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void testSuiteE_AutoRentShieldStompAndSingleLog() throws Exception {
        GatewayConfig config = new GatewayConfig(0, "dev", null, TEST_JWT_SECRET, 3600_000L);
        CloudflareOriginFilter originFilter = config.createOriginFilter();
        JwtTokenProvider jwtProvider = new JwtTokenProvider(config.getJwtSecret(), config.getJwtValidityMs());
        PasswordEncoderUtil passwordEncoder = new PasswordEncoderUtil();
        UserRepository userRepository = new UserRepository();
        TurnTimerService timerService = new TurnTimerService(
                Executors.newScheduledThreadPool(2, r -> {
                    Thread t = new Thread(r, "test-shield-timer");
                    t.setDaemon(true);
                    return t;
                }),
                30
        );
        RoomManagerService roomManager = new RoomManagerService(timerService);
        AuthRestController authController = new AuthRestController(userRepository, passwordEncoder, jwtProvider);
        RoomRestController roomController = new RoomRestController(roomManager);
        GameWebSocketController wsController = new GameWebSocketController();

        ApiGatewayServer server = new ApiGatewayServer(
                config, originFilter, jwtProvider, userRepository,
                roomManager, authController, roomController, wsController);
        server.start();
        try {
            int port = server.getPort();
            AuthUser p1 = registerViaRest(port, "shP1", "pass1234", "Shield User");
            AuthUser p2 = registerViaRest(port, "shP2", "pass1234", "Owner User");
            Map<String, String> h1 = Map.of("Authorization", "Bearer " + p1.token);
            Map<String, String> h2 = Map.of("Authorization", "Bearer " + p2.token);

            HttpResponse createRes = RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms", h1,
                    "{\"roomName\":\"Shield Arena\",\"maxPlayers\":2}");
            assertEquals(201, createRes.statusCode, "Create room -> 201");
            String roomId = (String) MiniJson.parseObject((String) MiniJson.parseObject(createRes.body).get("payload")).get("roomId");
            assertEquals(200, RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms/" + roomId + "/join", h2, "{}").statusCode,
                    "p2 joins");
            assertEquals(200, RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/rooms/" + roomId + "/start", h1, "{}").statusCode,
                    "Start -> 200");

            GameRoom room = roomManager.getRoom(roomId);
            Player player1 = room.getPlayers().stream()
                    .filter(p -> p1.userId.equals(p.getId())).findFirst().orElseThrow();
            Player player2 = room.getPlayers().stream()
                    .filter(p -> p2.userId.equals(p.getId())).findFirst().orElseThrow();
            StreetProperty tile3 = (StreetProperty) room.getBoard().getTile(3);
            player2.buyProperty(tile3);
            player1.getInventory().addItem(new RentShieldItem());
            room.setForcedDiceValue(3); // p1 lands on tile 3 owned by p2 -> auto-consumes RENT_SHIELD

            String topicDest = "/topic/room/" + roomId;
            try (StompWsClient c1 = new StompWsClient("127.0.0.1", port, "/ws", null)) {
                assertEquals("CONNECTED", c1.connectAndExpectFrame("Bearer " + p1.token, 4000).command, "c1 CONNECTED");
                c1.subscribe("sub-1", topicDest);
                awaitGameSnapshot(c1, topicDest, 4000);

                c1.send("/app/room/" + roomId + "/roll-dice", "{\"turnId\":" + room.getTurnId() + "}");

                Map<String, Object> itemMsg = awaitTopicMessageOfType(c1, topicDest, "ITEM_USED", 4000);
                Map<String, Object> itemPayload = MiniJson.parseObject((String) itemMsg.get("payload"));
                assertEquals("RENT_SHIELD", itemPayload.get("itemId"), "ITEM_USED itemId == RENT_SHIELD");
                assertEquals(Boolean.TRUE, itemPayload.get("auto"), "ITEM_USED auto == true");
                assertEquals(3, ((Number) itemPayload.get("tileIndex")).intValue(), "ITEM_USED tileIndex == 3");
                assertEquals(1_500_000, player1.getMoney(), "Auto RENT_SHIELD blocked rent deduction");

                // Verify exactly 1 canonical log line for auto RENT_SHIELD in timeline
                HttpResponse snapRes = RawHttpClient.request("127.0.0.1", port, "GET", "/api/v1/rooms/" + roomId + "/snapshot", h1, null);
                Map<String, Object> snap = MiniJson.parseObject((String) MiniJson.parseObject(snapRes.body).get("payload"));
                List<Object> timeline = (List<Object>) snap.get("timeline");
                long shieldLogs = timeline.stream()
                        .map(o -> String.valueOf(((Map<String, Object>) o).get("message")))
                        .filter(m -> m.contains("Khiên Hộ mệnh"))
                        .count();
                assertEquals(1L, shieldLogs, "Exactly 1 canonical log line for auto RENT_SHIELD");
            }
        } finally {
            server.stop();
            roomManager.shutdown();
        }
    }

    private Map<String, Object> awaitTopicMessageOfTypeOrNull(
            StompWsClient client, String dest, String expectedType, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            long rem = Math.max(10L, deadline - System.currentTimeMillis());
            StompFrame frame = client.awaitAndConsume(f -> {
                if (!"MESSAGE".equals(f.command) || !dest.equals(f.headers.get("destination"))) {
                    return false;
                }
                Map<String, Object> msg = MiniJson.parseObject(f.body);
                return expectedType.equals(msg.get("type"));
            }, rem);
            if (frame != null) {
                return MiniJson.parseObject(frame.body);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static int findPlayerPosition(Map<String, Object> snapshot, String playerId) {
        List<Object> players = (List<Object>) snapshot.get("players");
        for (Object obj : players) {
            Map<String, Object> p = (Map<String, Object>) obj;
            if (playerId.equals(p.get("id"))) {
                return ((Number) p.get("position")).intValue();
            }
        }
        throw new AssertionError("Player not found in snapshot: " + playerId);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> findTile(Map<String, Object> snapshot, int index) {
        List<Object> tiles = (List<Object>) snapshot.get("tiles");
        for (Object obj : tiles) {
            Map<String, Object> t = (Map<String, Object>) obj;
            if (((Number) t.get("index")).intValue() == index) {
                return t;
            }
        }
        throw new AssertionError("Tile not found in snapshot: " + index);
    }

    // =========================================================================
    // SERVER HARNESS & TEST HELPERS
    // =========================================================================

    private GatewayHarness startGateway(String mode, String originSecret) throws Exception {
        return startGatewayWithTurnTimeout(mode, originSecret, 30);
    }

    private GatewayHarness startGatewayWithTurnTimeout(String mode, String originSecret, int turnTimeoutSeconds) throws Exception {
        GatewayConfig config = new GatewayConfig(
                0,
                mode,
                originSecret,
                TEST_JWT_SECRET,
                3600_000L
        );
        CloudflareOriginFilter originFilter = config.createOriginFilter();
        JwtTokenProvider jwtProvider = new JwtTokenProvider(config.getJwtSecret(), config.getJwtValidityMs());
        PasswordEncoderUtil passwordEncoder = new PasswordEncoderUtil();
        UserRepository userRepository = new UserRepository();
        TurnTimerService timerService = new TurnTimerService(
                Executors.newScheduledThreadPool(2, r -> {
                    Thread t = new Thread(r, "test-turn-timer");
                    t.setDaemon(true);
                    return t;
                }),
                turnTimeoutSeconds
        );
        RoomManagerService roomManager = new RoomManagerService(timerService);
        AuthRestController authController = new AuthRestController(userRepository, passwordEncoder, jwtProvider);
        RoomRestController roomController = new RoomRestController(roomManager);
        GameWebSocketController wsController = new GameWebSocketController();

        ApiGatewayServer server = new ApiGatewayServer(
                config,
                originFilter,
                jwtProvider,
                userRepository,
                roomManager,
                authController,
                roomController,
                wsController
        );
        server.start();
        assertTrue(server.isRunning(), "ApiGatewayServer running");
        assertTrue(server.getPort() > 0, "Ephemeral port bound > 0");
        return new GatewayHarness(server.getPort(), () -> {
            server.stop();
            roomManager.shutdown();
            assertFalse(server.isRunning(), "ApiGatewayServer stopped cleanly");
        });
    }

    private static final class GatewayHarness implements AutoCloseable {
        final int port;
        private final ThrowingRunnable closer;

        GatewayHarness(int port, ThrowingRunnable closer) {
            this.port = port;
            this.closer = closer;
        }

        @Override
        public void close() {
            try {
                closer.run();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Failed to close GatewayHarness", e);
            }
        }
    }

    private static final class AuthUser {
        final String userId;
        final String username;
        final String token;

        AuthUser(String userId, String username, String token) {
            this.userId = userId;
            this.username = username;
            this.token = token;
        }
    }

    private AuthUser registerViaRest(int port, String username, String password, String displayName) throws Exception {
        String body = String.format("{\"username\":\"%s\",\"password\":\"%s\",\"displayName\":\"%s\"}",
                MiniJson.escape(username), MiniJson.escape(password), MiniJson.escape(displayName));
        HttpResponse res = RawHttpClient.request("127.0.0.1", port, "POST", "/api/v1/auth/register", null, body);
        assertEquals(201, res.statusCode, "Register " + username + " failed: " + res.body);
        Map<String, Object> msg = MiniJson.parseObject(res.body);
        Map<String, Object> payload = MiniJson.parseObject((String) msg.get("payload"));
        return new AuthUser((String) payload.get("userId"), username, (String) payload.get("accessToken"));
    }

    private static String extractErrorCode(String responseBody) {
        Map<String, Object> outer = MiniJson.parseObject(responseBody);
        if (outer.get("payload") instanceof String) {
            Map<String, Object> inner = MiniJson.parseObject((String) outer.get("payload"));
            if (inner.get("error") != null) {
                return String.valueOf(inner.get("error"));
            }
        }
        return outer.get("error") == null ? null : String.valueOf(outer.get("error"));
    }

    private static Map<String, Object> awaitTopicMessageOfType(
            StompWsClient client, String dest, String expectedType, long timeoutMs) throws Exception {
        StompFrame frame = client.awaitAndConsume(f -> {
            if (!"MESSAGE".equals(f.command) || !dest.equals(f.headers.get("destination"))) {
                return false;
            }
            Map<String, Object> json = MiniJson.parseObject(f.body);
            return expectedType.equals(json.get("type"));
        }, timeoutMs);
        assertNotNull(frame, "Expected STOMP MESSAGE type=" + expectedType + " on " + dest);
        return MiniJson.parseObject(frame.body);
    }

    private static Map<String, Object> awaitGameSnapshot(
            StompWsClient client, String dest, long timeoutMs) throws Exception {
        Map<String, Object> msg = awaitTopicMessageOfType(client, dest, "GAME_STATE_SNAPSHOT", timeoutMs);
        return MiniJson.parseObject((String) msg.get("payload"));
    }

    private static Map<String, Object> awaitGameError(
            StompWsClient client, String errDest, long timeoutMs) throws Exception {
        StompFrame frame = client.awaitAndConsume(
                f -> "MESSAGE".equals(f.command) && errDest.equals(f.headers.get("destination")),
                timeoutMs
        );
        assertNotNull(frame, "Expected error MESSAGE on " + errDest);
        Map<String, Object> msg = MiniJson.parseObject(frame.body);
        assertEquals("ERROR", msg.get("type"), "Error frame type ERROR");
        return MiniJson.parseObject((String) msg.get("payload"));
    }

    private static void assertSnapshotsConsistent(Map<String, Object> s1, Map<String, Object> s2) {
        assertEquals(s1.get("roomStatus"), s2.get("roomStatus"), "Snapshot roomStatus consistent");
        assertEquals(s1.get("turnId"), s2.get("turnId"), "Snapshot turnId consistent");
        assertEquals(s1.get("phase"), s2.get("phase"), "Snapshot phase consistent");
        assertEquals(s1.get("currentPlayerId"), s2.get("currentPlayerId"), "Snapshot currentPlayerId consistent");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> findPlayerInSnapshot(Map<String, Object> snap, String playerId) {
        List<Object> players = (List<Object>) snap.get("players");
        for (Object o : players) {
            Map<String, Object> p = (Map<String, Object>) o;
            if (playerId.equals(p.get("id"))) {
                return p;
            }
        }
        throw new AssertionError("Player " + playerId + " not found in snapshot");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> findTileInSnapshot(Map<String, Object> snap, int index) {
        List<Object> tiles = (List<Object>) snap.get("tiles");
        for (Object o : tiles) {
            Map<String, Object> t = (Map<String, Object>) o;
            if (((Number) t.get("index")).intValue() == index) {
                return t;
            }
        }
        throw new AssertionError("Tile " + index + " not found in snapshot");
    }

    // =========================================================================
    // ASSERTION HELPERS
    // =========================================================================

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    private static void assertFalse(boolean cond, String msg) {
        if (cond) throw new AssertionError(msg);
    }

    private static void assertNotNull(Object obj, String msg) {
        if (obj == null) throw new AssertionError(msg + " (was null)");
    }

    private static void assertNull(Object obj, String msg) {
        if (obj != null) throw new AssertionError(msg + " (expected null, got " + obj + ")");
    }

    private static void assertEquals(Object expected, Object actual, String msg) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(msg + " (expected <" + expected + "> but got <" + actual + ">)");
        }
    }

    private static void assertEquals(int expected, int actual, String msg) {
        if (expected != actual) {
            throw new AssertionError(msg + " (expected <" + expected + "> but got <" + actual + ">)");
        }
    }

    private static void assertEquals(long expected, long actual, String msg) {
        if (expected != actual) {
            throw new AssertionError(msg + " (expected <" + expected + "> but got <" + actual + ">)");
        }
    }

    private static void assertThrows(Class<? extends Throwable> exType, Runnable r, String msg) {
        try {
            r.run();
        } catch (Throwable t) {
            if (exType.isInstance(t)) {
                return;
            }
            throw new AssertionError(msg + " (wrong exception " + t.getClass().getName() + ")", t);
        }
        throw new AssertionError(msg + " (expected " + exType.getSimpleName() + " but no exception thrown)");
    }

    // =========================================================================
    // RAW TCP HTTP/1.1 CLIENT
    // =========================================================================

    private static final class HttpResponse {
        final int statusCode;
        final String statusLine;
        final Map<String, String> headers;
        final String body;

        HttpResponse(int statusCode, String statusLine, Map<String, String> headers, String body) {
            this.statusCode = statusCode;
            this.statusLine = statusLine;
            this.headers = headers;
            this.body = body;
        }
    }

    private static final class RawHttpClient {
        static HttpResponse request(String host, int port, String method, String path,
                                    Map<String, String> headers, String body) throws IOException {
            try (Socket socket = new Socket(host, port)) {
                socket.setSoTimeout(10_000);
                OutputStream out = socket.getOutputStream();
                InputStream in = socket.getInputStream();

                byte[] bodyBytes = body == null ? null : body.getBytes(StandardCharsets.UTF_8);
                StringBuilder req = new StringBuilder();
                req.append(method).append(' ').append(path).append(" HTTP/1.1\r\n");
                req.append("Host: ").append(host).append(':').append(port).append("\r\n");
                req.append("Connection: close\r\n");
                if (headers != null) {
                    for (Map.Entry<String, String> h : headers.entrySet()) {
                        req.append(h.getKey()).append(": ").append(h.getValue()).append("\r\n");
                    }
                }
                if (bodyBytes != null) {
                    boolean hasContentType = headers != null && headers.keySet().stream()
                            .anyMatch(k -> k.equalsIgnoreCase("Content-Type"));
                    if (!hasContentType) {
                        req.append("Content-Type: application/json; charset=utf-8\r\n");
                    }
                    req.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
                }
                req.append("\r\n");

                try {
                    out.write(req.toString().getBytes(StandardCharsets.US_ASCII));
                    if (bodyBytes != null) {
                        out.write(bodyBytes);
                    }
                    out.flush();
                } catch (IOException ignoredOnEarlyReject) {
                    // Server may respond (e.g. 413) and close before draining >100KB body
                }

                String headerBlock = readHeaderBlock(in);
                if (headerBlock == null || headerBlock.isEmpty()) {
                    throw new IOException("Empty HTTP response from server");
                }
                String[] lines = headerBlock.split("\r\n");
                String statusLine = lines[0];
                String[] parts = statusLine.split("\\s+", 3);
                int statusCode = Integer.parseInt(parts[1]);

                Map<String, String> respHeaders = new LinkedHashMap<>();
                for (int i = 1; i < lines.length; i++) {
                    int idx = lines[i].indexOf(':');
                    if (idx > 0) {
                        String k = lines[i].substring(0, idx).trim().toLowerCase(Locale.ROOT);
                        String v = lines[i].substring(idx + 1).trim();
                        respHeaders.put(k, v);
                    }
                }

                byte[] respBytes;
                String te = respHeaders.getOrDefault("transfer-encoding", "");
                if (te.toLowerCase(Locale.ROOT).contains("chunked")) {
                    respBytes = readChunked(in);
                } else if (respHeaders.containsKey("content-length")) {
                    int len = Integer.parseInt(respHeaders.get("content-length"));
                    respBytes = readExactOrEof(in, len);
                } else if (statusCode == 204 || statusCode == 304) {
                    respBytes = new byte[0];
                } else {
                    respBytes = in.readAllBytes();
                }

                return new HttpResponse(statusCode, statusLine, respHeaders,
                        new String(respBytes, StandardCharsets.UTF_8));
            }
        }

        static String readHeaderBlock(InputStream in) throws IOException {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            int prev3 = -1, prev2 = -1, prev1 = -1, b;
            while ((b = in.read()) != -1) {
                buf.write(b);
                if (prev3 == '\r' && prev2 == '\n' && prev1 == '\r' && b == '\n') {
                    byte[] arr = buf.toByteArray();
                    return new String(arr, 0, arr.length - 4, StandardCharsets.ISO_8859_1);
                }
                prev3 = prev2;
                prev2 = prev1;
                prev1 = b;
            }
            return buf.toString(StandardCharsets.ISO_8859_1);
        }

        private static byte[] readExactOrEof(InputStream in, int len) throws IOException {
            if (len <= 0) return new byte[0];
            byte[] out = new byte[len];
            int off = 0;
            while (off < len) {
                int r = in.read(out, off, len - off);
                if (r == -1) break;
                off += r;
            }
            return off == len ? out : Arrays.copyOf(out, off);
        }

        private static byte[] readChunked(InputStream in) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            while (true) {
                String line = readLineCrLf(in);
                if (line == null) break;
                int semi = line.indexOf(';');
                if (semi >= 0) line = line.substring(0, semi);
                int size = Integer.parseInt(line.trim(), 16);
                if (size == 0) break;
                byte[] chunk = in.readNBytes(size);
                out.write(chunk);
                readLineCrLf(in);
            }
            return out.toByteArray();
        }

        private static String readLineCrLf(InputStream in) throws IOException {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            int prev = -1, b;
            while ((b = in.read()) != -1) {
                if (prev == '\r' && b == '\n') {
                    byte[] arr = buf.toByteArray();
                    return new String(arr, 0, arr.length - 1, StandardCharsets.ISO_8859_1);
                }
                buf.write(b);
                prev = b;
            }
            return buf.size() == 0 ? null : buf.toString(StandardCharsets.ISO_8859_1);
        }
    }

    // =========================================================================
    // RFC 6455 WEBSOCKET + STOMP 1.2 CLIENT
    // =========================================================================

    private static final class WsUpgradeResult {
        final int statusCode;
        final Map<String, String> headers;
        final String expectedAccept;
        final Socket socket;
        final InputStream in;
        final OutputStream out;

        WsUpgradeResult(int statusCode, Map<String, String> headers, String expectedAccept,
                        Socket socket, InputStream in, OutputStream out) {
            this.statusCode = statusCode;
            this.headers = headers;
            this.expectedAccept = expectedAccept;
            this.socket = socket;
            this.in = in;
            this.out = out;
        }

        void closeQuietly() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static final class StompFrame {
        final String command;
        final Map<String, String> headers;
        final String body;

        StompFrame(String command, Map<String, String> headers, String body) {
            this.command = command;
            this.headers = headers;
            this.body = body;
        }
    }

    private static final class StompWsClient implements AutoCloseable {
        private static final SecureRandom RNG = new SecureRandom();
        private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final List<StompFrame> inbox = new ArrayList<>();
        private volatile boolean closed = false;
        private final Thread readerThread;

        StompWsClient(String host, int port, String path, Map<String, String> extraHeaders) throws Exception {
            WsUpgradeResult up = upgradeOnly(host, port, path, extraHeaders);
            if (up.statusCode != 101) {
                up.closeQuietly();
                throw new IOException("WebSocket upgrade failed with status " + up.statusCode);
            }
            if (!up.expectedAccept.equals(up.headers.get("sec-websocket-accept"))) {
                up.closeQuietly();
                throw new IOException("Invalid Sec-WebSocket-Accept header");
            }
            this.socket = up.socket;
            this.socket.setSoTimeout(0);
            this.in = up.in;
            this.out = up.out;
            this.readerThread = new Thread(this::readLoop, "ws-stomp-reader");
            this.readerThread.setDaemon(true);
            this.readerThread.start();
        }

        static WsUpgradeResult upgradeOnly(String host, int port, String path,
                                           Map<String, String> extraHeaders) throws Exception {
            byte[] nonce = new byte[16];
            RNG.nextBytes(nonce);
            String wsKey = Base64.getEncoder().encodeToString(nonce);
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            String expectedAccept = Base64.getEncoder().encodeToString(
                    sha1.digest((wsKey + WS_GUID).getBytes(StandardCharsets.US_ASCII)));

            Socket sock = new Socket(host, port);
            sock.setSoTimeout(10_000);
            OutputStream out = sock.getOutputStream();
            InputStream in = sock.getInputStream();

            StringBuilder req = new StringBuilder();
            req.append("GET ").append(path).append(" HTTP/1.1\r\n");
            req.append("Host: ").append(host).append(':').append(port).append("\r\n");
            req.append("Upgrade: websocket\r\n");
            req.append("Connection: Upgrade\r\n");
            req.append("Sec-WebSocket-Key: ").append(wsKey).append("\r\n");
            req.append("Sec-WebSocket-Version: 13\r\n");
            req.append("Sec-WebSocket-Protocol: v12.stomp, v11.stomp, v10.stomp\r\n");
            if (extraHeaders != null) {
                for (Map.Entry<String, String> e : extraHeaders.entrySet()) {
                    req.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
                }
            }
            req.append("\r\n");
            out.write(req.toString().getBytes(StandardCharsets.US_ASCII));
            out.flush();

            String headerBlock = RawHttpClient.readHeaderBlock(in);
            String[] lines = headerBlock.split("\r\n");
            int status = Integer.parseInt(lines[0].split("\\s+", 3)[1]);
            Map<String, String> respHeaders = new LinkedHashMap<>();
            for (int i = 1; i < lines.length; i++) {
                int idx = lines[i].indexOf(':');
                if (idx > 0) {
                    respHeaders.put(lines[i].substring(0, idx).trim().toLowerCase(Locale.ROOT),
                            lines[i].substring(idx + 1).trim());
                }
            }
            return new WsUpgradeResult(status, respHeaders, expectedAccept, sock, in, out);
        }

        StompFrame connectAndExpectFrame(String authorizationHeader, long timeoutMs) throws Exception {
            return connectAndExpectFrame(authorizationHeader, "0,0", timeoutMs);
        }

        StompFrame connectAndExpectFrame(String authorizationHeader, String heartBeatHeader, long timeoutMs) throws Exception {
            StringBuilder frame = new StringBuilder();
            frame.append("CONNECT\n");
            frame.append("accept-version:1.2,1.1,1.0\n");
            frame.append("host:localhost\n");
            frame.append("heart-beat:").append(heartBeatHeader == null ? "0,0" : heartBeatHeader).append('\n');
            if (authorizationHeader != null) {
                frame.append("Authorization:").append(authorizationHeader).append('\n');
            }
            frame.append('\n').append('\0');
            sendWsText(frame.toString());
            return awaitAndConsume(f -> "CONNECTED".equals(f.command) || "ERROR".equals(f.command), timeoutMs);
        }

        void subscribe(String id, String destination) throws IOException {
            String frame = "SUBSCRIBE\n"
                    + "id:" + id + "\n"
                    + "destination:" + destination + "\n"
                    + "\n\0";
            sendWsText(frame);
        }

        void send(String destination, String jsonBody) throws IOException {
            String frame = "SEND\n"
                    + "destination:" + destination + "\n"
                    + "content-type:application/json\n"
                    + "\n"
                    + (jsonBody == null ? "" : jsonBody)
                    + "\0";
            sendWsText(frame);
        }

        StompFrame awaitAndConsume(Predicate<StompFrame> pred, long timeoutMs) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            synchronized (inbox) {
                while (true) {
                    for (int i = 0; i < inbox.size(); i++) {
                        StompFrame f = inbox.get(i);
                        if (pred.test(f)) {
                            inbox.remove(i);
                            return f;
                        }
                    }
                    if (closed) {
                        return null;
                    }
                    long rem = deadline - System.currentTimeMillis();
                    if (rem <= 0) {
                        return null;
                    }
                    inbox.wait(rem);
                }
            }
        }

        private synchronized void sendWsText(String text) throws IOException {
            sendWsFrame(0x1, text.getBytes(StandardCharsets.UTF_8));
        }

        private synchronized void sendWsFrame(int opcode, byte[] payload) throws IOException {
            byte[] mask = new byte[4];
            RNG.nextBytes(mask);
            ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
            outBuf.write(0x80 | (opcode & 0x0F));
            int len = payload.length;
            if (len <= 125) {
                outBuf.write(0x80 | len);
            } else if (len <= 65535) {
                outBuf.write(0x80 | 126);
                outBuf.write((len >> 8) & 0xFF);
                outBuf.write(len & 0xFF);
            } else {
                outBuf.write(0x80 | 127);
                long l = len;
                for (int shift = 56; shift >= 0; shift -= 8) {
                    outBuf.write((int) ((l >> shift) & 0xFF));
                }
            }
            outBuf.write(mask);
            for (int i = 0; i < len; i++) {
                outBuf.write(payload[i] ^ mask[i % 4]);
            }
            out.write(outBuf.toByteArray());
            out.flush();
        }

        private void readLoop() {
            StringBuilder stompBuffer = new StringBuilder();
            ByteArrayOutputStream fragBuf = new ByteArrayOutputStream();
            int fragOpcode = -1;
            try {
                while (!closed) {
                    int b0 = in.read();
                    if (b0 == -1) break;
                    int b1 = in.read();
                    if (b1 == -1) break;

                    boolean fin = (b0 & 0x80) != 0;
                    int opcode = b0 & 0x0F;
                    boolean masked = (b1 & 0x80) != 0;
                    long len = b1 & 0x7F;
                    if (len == 126) {
                        int hi = in.read(), lo = in.read();
                        if (hi == -1 || lo == -1) break;
                        len = ((hi & 0xFF) << 8) | (lo & 0xFF);
                    } else if (len == 127) {
                        len = 0;
                        for (int i = 0; i < 8; i++) {
                            int x = in.read();
                            if (x == -1) throw new EOFException();
                            len = (len << 8) | (x & 0xFF);
                        }
                    }
                    byte[] mask = null;
                    if (masked) {
                        mask = in.readNBytes(4);
                        if (mask.length < 4) break;
                    }
                    byte[] payload = in.readNBytes((int) len);
                    if (payload.length < len) break;
                    if (masked && mask != null) {
                        for (int i = 0; i < payload.length; i++) {
                            payload[i] ^= mask[i % 4];
                        }
                    }

                    if (opcode == 0x8) { // CLOSE
                        try {
                            sendWsFrame(0x8, new byte[0]);
                        } catch (IOException ignored) {
                        }
                        break;
                    } else if (opcode == 0x9) { // PING -> PONG
                        sendWsFrame(0xA, payload);
                        continue;
                    } else if (opcode == 0xA) { // PONG
                        continue;
                    }

                    if (opcode != 0x0) {
                        fragOpcode = opcode;
                        fragBuf.reset();
                    }
                    fragBuf.write(payload);
                    if (fin && fragOpcode == 0x1) {
                        String text = fragBuf.toString(StandardCharsets.UTF_8);
                        fragBuf.reset();
                        fragOpcode = -1;
                        stompBuffer.append(text);
                        drainStompFrames(stompBuffer);
                    }
                }
            } catch (IOException ignored) {
            } finally {
                closed = true;
                synchronized (inbox) {
                    inbox.notifyAll();
                }
            }
        }

        private void drainStompFrames(StringBuilder buf) {
            while (true) {
                // Trim leading heartbeat newlines
                while (buf.length() > 0 && (buf.charAt(0) == '\n' || buf.charAt(0) == '\r')) {
                    buf.deleteCharAt(0);
                }
                int nulIdx = buf.indexOf("\0");
                if (nulIdx < 0) {
                    return;
                }
                String raw = buf.substring(0, nulIdx);
                buf.delete(0, nulIdx + 1);
                StompFrame parsed = parseStompFrame(raw);
                if (parsed != null) {
                    synchronized (inbox) {
                        inbox.add(parsed);
                        inbox.notifyAll();
                    }
                }
            }
        }

        private static StompFrame parseStompFrame(String raw) {
            String normalized = raw.replace("\r\n", "\n");
            int split = normalized.indexOf("\n\n");
            String headerPart = split >= 0 ? normalized.substring(0, split) : normalized;
            String body = split >= 0 ? normalized.substring(split + 2) : "";
            String[] lines = headerPart.split("\n");
            if (lines.length == 0 || lines[0].isBlank()) {
                return null;
            }
            String cmd = lines[0].trim();
            Map<String, String> headers = new LinkedHashMap<>();
            for (int i = 1; i < lines.length; i++) {
                int colon = lines[i].indexOf(':');
                if (colon > 0) {
                    String k = lines[i].substring(0, colon);
                    String v = lines[i].substring(colon + 1);
                    headers.put(k, v);
                }
            }
            return new StompFrame(cmd, headers, body);
        }

        @Override
        public void close() {
            closed = true;
            try {
                sendWsText("DISCONNECT\n\n\0");
                sendWsFrame(0x8, new byte[0]);
            } catch (IOException ignored) {
            }
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            synchronized (inbox) {
                inbox.notifyAll();
            }
        }
    }

    // =========================================================================
    // PURE-JDK MINIMAL JSON PARSER
    // =========================================================================

    private static final class MiniJson {
        private final String s;
        private int pos;

        private MiniJson(String s) {
            this.s = s;
        }

        @SuppressWarnings("unchecked")
        static Map<String, Object> parseObject(String json) {
            Object val = parse(json);
            if (!(val instanceof Map)) {
                throw new IllegalArgumentException("Expected JSON object, got: " + json);
            }
            return (Map<String, Object>) val;
        }

        @SuppressWarnings("unchecked")
        static List<Object> parseArray(String json) {
            Object val = parse(json);
            if (!(val instanceof List)) {
                throw new IllegalArgumentException("Expected JSON array, got: " + json);
            }
            return (List<Object>) val;
        }

        static Object parse(String json) {
            if (json == null) throw new IllegalArgumentException("JSON is null");
            MiniJson p = new MiniJson(json);
            Object v = p.readValue();
            p.skipWs();
            return v;
        }

        static String escape(String text) {
            if (text == null) return "";
            StringBuilder sb = new StringBuilder(text.length());
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\b' -> sb.append("\\b");
                    case '\f' -> sb.append("\\f");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> {
                        if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                        else sb.append(c);
                    }
                }
            }
            return sb.toString();
        }

        private Object readValue() {
            skipWs();
            if (pos >= s.length()) throw new IllegalArgumentException("Unexpected end of JSON");
            char c = s.charAt(pos);
            return switch (c) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                case 'n' -> readLiteral("null", null);
                default -> readNumber();
            };
        }

        private Map<String, Object> readObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // '{'
            skipWs();
            if (pos < s.length() && s.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (pos < s.length()) {
                skipWs();
                String key = readString();
                skipWs();
                expect(':');
                Object val = readValue();
                map.put(key, val);
                skipWs();
                if (pos < s.length() && s.charAt(pos) == ',') {
                    pos++;
                } else {
                    break;
                }
            }
            skipWs();
            expect('}');
            return map;
        }

        private List<Object> readArray() {
            List<Object> list = new ArrayList<>();
            pos++; // '['
            skipWs();
            if (pos < s.length() && s.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (pos < s.length()) {
                list.add(readValue());
                skipWs();
                if (pos < s.length() && s.charAt(pos) == ',') {
                    pos++;
                } else {
                    break;
                }
            }
            skipWs();
            expect(']');
            return list;
        }

        private String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < s.length()) {
                char c = s.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char esc = s.charAt(pos++);
                    switch (esc) {
                        case '"', '\\', '/' -> sb.append(esc);
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            String hex = s.substring(pos, pos + 4);
                            pos += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                        }
                        default -> throw new IllegalArgumentException("Bad escape: \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        private Object readLiteral(String lit, Object val) {
            if (!s.startsWith(lit, pos)) {
                throw new IllegalArgumentException("Expected " + lit + " at pos " + pos);
            }
            pos += lit.length();
            return val;
        }

        private Number readNumber() {
            int start = pos;
            if (s.charAt(pos) == '-') pos++;
            while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            boolean isFloat = false;
            if (pos < s.length() && s.charAt(pos) == '.') {
                isFloat = true;
                pos++;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            }
            if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                isFloat = true;
                pos++;
                if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) pos++;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            }
            String num = s.substring(start, pos);
            return isFloat ? Double.parseDouble(num) : Long.parseLong(num);
        }

        private void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        private void expect(char expected) {
            if (pos >= s.length() || s.charAt(pos) != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' at pos " + pos);
            }
            pos++;
        }
    }
}
