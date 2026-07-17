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

test("실행 가능한 동작은 stateToken만 보내고 완료 뒤 목록과 상세를 다시 조회한다", async () => {
    const harness = await createHarness();
    const action = executableAction("INSTRUCTOR_ACCEPT");
    const before = actionableDetail(7, "token-before", "WAITING_FOR_INSTRUCTOR", action);
    const after = actionableDetail(7, "token-after", "WAITING_FOR_CONFIRMATION", executableAction("CONSUMER_ACCEPT"));
    harness.control.state.requests = [requestSummary(7)];
    harness.control.state.selectedId = 7;
    harness.control.state.detail = before;
    harness.elements.dialog.open = true;

    const execution = harness.control.executeAction(action);
    await harness.control.executeAction(action);
    const postRequest = harness.takePending();

    assert.equal(postRequest.path, "/dev/matching/requests/7/actions");
    assert.equal(postRequest.options.method, "POST");
    assert.deepEqual(JSON.parse(postRequest.options.body), {
        actionKey: "INSTRUCTOR_ACCEPT",
        stateToken: "token-before"
    });
    assert.equal(harness.pending.length, 0, "중복 클릭은 두 번째 POST를 만들지 않아야 합니다.");

    postRequest.resolve(success({actionKey: "INSTRUCTOR_ACCEPT", actor: action.actor, before, after}));
    await waitForPending(harness.pending);
    harness.takePending().resolve(success(listPayload({
        requests: [{...requestSummary(7), matchingStatus: "WAITING_FOR_CONFIRMATION"}],
        totalPages: 1,
        totalElements: 1
    })));
    await waitForPending(harness.pending);
    harness.takePending().resolve(success(after));
    await execution;

    assert.equal(harness.control.state.detail.stateToken, "token-after");
    assert.equal(harness.control.state.actionLoading, false);
    assert.equal(harness.elements.dialog.open, false);
    assert.deepEqual(harness.fetchCalls, [
        "/dev/matching/requests/7/actions",
        "/dev/matching/requests?page=0&size=50",
        "/dev/matching/requests/7"
    ]);
});

test("409 상태충돌이면 modal을 닫고 최신 상세를 자동으로 다시 조회한다", async () => {
    const harness = await createHarness();
    const action = executableAction("INSTRUCTOR_ACCEPT");
    harness.control.state.requests = [requestSummary(8)];
    harness.control.state.selectedId = 8;
    harness.control.state.detail = actionableDetail(8, "stale-token", "WAITING_FOR_INSTRUCTOR", action);
    harness.elements.dialog.open = true;

    const execution = harness.control.executeAction(action);
    harness.takePending().resolve(failure(
        "조회한 뒤 매칭 상태가 바뀌었습니다.",
        409,
        "DEV_MATCHING_STATE_CHANGED"
    ));
    await waitForPending(harness.pending);
    harness.takePending().resolve(success(actionableDetail(
        8,
        "latest-token",
        "WAITING_FOR_CONFIRMATION",
        executableAction("CONSUMER_ACCEPT")
    )));
    await execution;

    assert.equal(harness.control.state.detail.stateToken, "latest-token");
    assert.equal(harness.elements.dialog.open, false);
    assert.deepEqual(harness.fetchCalls, [
        "/dev/matching/requests/8/actions",
        "/dev/matching/requests/8"
    ]);
});

async function createHarness() {
    assert.ok(SCRIPT, "inline script를 찾을 수 없습니다.");
    const elements = new Map();
    const pending = [];
    const fetchCalls = [];
    const fetchRequests = [];
    let control;

    const element = selector => {
        if (!elements.has(selector)) elements.set(selector, fakeElement());
        return elements.get(selector);
    };
    const document = {
        querySelector: selector => element(selector)
    };
    const fetch = (path, options = {}) => {
        fetchCalls.push(path);
        fetchRequests.push({path, options});
        return new Promise((resolve, reject) => pending.push({path, options, resolve, reject}));
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
        fetchRequests,
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

function actionableDetail(id, stateToken, matchingStatus, action) {
    return {
        ...detailPayload(id),
        stateToken,
        matchingStatus,
        requestStatus: "MATCHED",
        availableActions: [action]
    };
}

function executableAction(actionKey) {
    const actor = actionKey === "INSTRUCTOR_ACCEPT"
        ? {personRole: "INSTRUCTOR", memberId: 45, instructorProfileId: 5, personaKey: "instructor", displayName: "강사"}
        : {personRole: "CONSUMER", memberId: 12, instructorProfileId: null, personaKey: "consumer", displayName: "강습생"};
    return {
        actionKey,
        label: actionKey,
        actor,
        affectedPeople: [actor],
        affectedResources: [],
        outcomes: [],
        previewOnly: false
    };
}

function success(data) {
    return {
        ok: true,
        status: 200,
        json: async () => ({success: true, data})
    };
}

function failure(message, status = 500, code = "INTERNAL_ERROR") {
    return {
        ok: false,
        status,
        json: async () => ({success: false, code, message})
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
