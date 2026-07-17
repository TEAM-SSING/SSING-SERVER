import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";
import vm from "node:vm";

const HTML_PATH = new URL("../../main/resources/dev-matching-console.html", import.meta.url);
const SCRIPT = readFileSync(HTML_PATH, "utf8").match(/<script>([\s\S]*?)<\/script>/)?.[1];

test("늦게 도착한 이전 상세 응답이 마지막 선택을 덮지 않는다", async () => {
    const harness = await createHarness();
    harness.control.state.requests = [requestSummary(1), requestSummary(2)];
    harness.elements.dialog.open = true;

    const firstLoad = harness.control.loadDetail(1);
    const firstRequest = harness.takePending();
    assert.equal(harness.elements.dialog.open, false);

    const secondLoad = harness.control.loadDetail(2);
    const secondRequest = harness.takePending();
    secondRequest.resolve(success(detailPayload(2)));
    await secondLoad;
    assert.equal(harness.control.state.detail.matchingRequestId, 2);

    firstRequest.resolve(success(detailPayload(1)));
    await firstLoad;

    assert.equal(harness.control.state.selectedId, 2);
    assert.equal(harness.control.state.detail.matchingRequestId, 2);
    assert.match(harness.elements.detailMeta.textContent, /request #2/);
});

test("목록 0건과 목록·상세 오류에서 이전 상세와 modal을 정해진 범위로 비운다", async () => {
    const harness = await createHarness();

    setStaleState(harness, 7);
    const emptyLoad = harness.control.loadList(true);
    harness.takePending().resolve(success(listPayload({requests: [], totalPages: 0, totalElements: 0})));
    await emptyLoad;
    assertCleared(harness);

    setStaleState(harness, 8);
    const failedList = harness.control.loadList(true);
    harness.takePending().resolve(failure("목록 오류"));
    await failedList;
    assertCleared(harness);
    assert.equal(harness.elements.listSummary.textContent, "조회 실패");

    harness.control.state.requests = [requestSummary(9)];
    harness.control.state.selectedId = 9;
    harness.control.state.detail = detailPayload(9);
    harness.elements.dialog.open = true;
    const failedDetail = harness.control.loadDetail(9);
    harness.takePending().resolve(failure("상세 오류"));
    await failedDetail;

    assert.equal(harness.control.state.requests.length, 1);
    assert.equal(harness.control.state.selectedId, 9);
    assert.equal(harness.control.state.detail, null);
    assert.equal(harness.elements.dialog.open, false);
    assert.match(harness.elements.detailMeta.textContent, /조회 실패/);
});

test("페이지가 줄어 범위를 벗어나면 마지막 페이지를 한 번만 다시 조회한다", async () => {
    const harness = await createHarness();
    harness.fetchCalls.length = 0;
    harness.control.state.page = 4;

    const load = harness.control.loadList(false);
    harness.takePending().resolve(success(listPayload({
        requests: [requestSummary(99)],
        totalPages: 2,
        totalElements: 2
    })));
    await waitForPending(harness.pending);
    harness.takePending().resolve(success(listPayload({
        requests: [],
        totalPages: 2,
        totalElements: 2
    })));
    await load;

    assert.deepEqual(harness.fetchCalls, [
        "/dev/matching/requests?page=4&size=50",
        "/dev/matching/requests?page=1&size=50"
    ]);
    assert.equal(harness.control.state.page, 1);
    assert.equal(harness.pending.length, 0);
});

async function createHarness() {
    assert.ok(SCRIPT, "inline script를 찾을 수 없습니다.");
    const elements = new Map();
    const pending = [];
    const fetchCalls = [];
    let control;

    const element = selector => {
        if (!elements.has(selector)) elements.set(selector, fakeElement());
        return elements.get(selector);
    };
    const document = {
        querySelector: selector => element(selector)
    };
    const fetch = path => {
        fetchCalls.push(path);
        return new Promise((resolve, reject) => pending.push({path, resolve, reject}));
    };
    const context = vm.createContext({
        document,
        fetch,
        console,
        Date,
        Intl,
        Number,
        Set,
        String,
        Promise,
        setInterval: () => 1,
        clearInterval: () => {},
        setTimeout: () => 1,
        clearTimeout: () => {},
        __SSING_DEV_MATCHING_TEST_HOOK__: value => {
            control = value;
        }
    });

    vm.runInContext(SCRIPT, context, {filename: "dev-matching-console.html"});
    pending.shift().resolve(success(listPayload({requests: [], totalPages: 0, totalElements: 0})));
    await control.initialLoadPromise;
    fetchCalls.length = 0;

    return {
        control,
        elements: {
            dialog: element("#actionDialog"),
            detail: element("#detail"),
            detailMeta: element("#detailMeta"),
            listSummary: element("#listSummary")
        },
        pending,
        fetchCalls,
        takePending() {
            assert.ok(pending.length > 0, "대기 중인 fetch가 없습니다.");
            return pending.shift();
        }
    };
}

function fakeElement() {
    const classes = new Set();
    return {
        value: "",
        checked: false,
        disabled: false,
        textContent: "",
        innerHTML: "",
        open: false,
        dataset: {},
        classList: {
            add: value => classes.add(value),
            remove: value => classes.delete(value),
            contains: value => classes.has(value)
        },
        addEventListener() {},
        querySelectorAll() {
            return [];
        },
        close() {
            this.open = false;
        },
        showModal() {
            this.open = true;
        }
    };
}

function listPayload({requests, totalPages, totalElements}) {
    return {
        observedAt: "2026-07-16T00:00:00Z",
        requests,
        totalPages,
        totalElements
    };
}

function requestSummary(id) {
    return {
        matchingRequestId: id,
        consumerMemberId: id + 100,
        consumerPersonaKey: `consumer-${id}`,
        consumerName: `강습생 ${id}`,
        resolutionState: "RESOLVED",
        matchingStatus: "SEARCHING",
        requestStatus: "REQUESTED",
        requestStatusReason: null,
        groupId: null,
        offerId: null,
        availableActionKeys: []
    };
}

function detailPayload(id) {
    return {
        observedAt: "2026-07-16T00:00:00Z",
        stateToken: `token-${id}`,
        matchingRequestId: id,
        resolutionState: "RESOLVED",
        matchingStatus: "SEARCHING",
        requestStatus: "REQUESTED",
        requestStatusReason: null,
        people: [],
        requestRelations: [],
        participants: [],
        resources: [],
        availableActions: [],
        diagnostics: [],
        actionLimitations: []
    };
}

function success(data) {
    return {
        ok: true,
        status: 200,
        json: async () => ({success: true, data})
    };
}

function failure(message) {
    return {
        ok: false,
        status: 500,
        json: async () => ({success: false, message})
    };
}

function setStaleState(harness, id) {
    harness.control.state.requests = [requestSummary(id)];
    harness.control.state.totalPages = 3;
    harness.control.state.totalElements = 3;
    harness.control.state.selectedId = id;
    harness.control.state.detail = detailPayload(id);
    harness.elements.dialog.open = true;
}

function assertCleared(harness) {
    assert.equal(harness.control.state.requests.length, 0);
    assert.equal(harness.control.state.totalPages, 0);
    assert.equal(harness.control.state.totalElements, 0);
    assert.equal(harness.control.state.selectedId, null);
    assert.equal(harness.control.state.detail, null);
    assert.equal(harness.elements.dialog.open, false);
}

async function waitForPending(pending) {
    for (let attempt = 0; attempt < 10 && pending.length === 0; attempt += 1) {
        await new Promise(resolve => setImmediate(resolve));
    }
    assert.ok(pending.length > 0, "재조회 fetch가 시작되지 않았습니다.");
}
