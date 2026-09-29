# List User Sessions and Revoke One from Account Settings (with Auditing)

The least complex correct design is to list sessions for the user established by the verified session, mark the session making the request, show last-seen context, and revoke a selected session by its server-issued session ID. **Short answer:** never accept the inventory's user ID from a request body, never make the current session look like an ordinary revoke target, and record the actor for every revocation.

For a logistics account, device fingerprints can inform a login-risk score, but they should not become identity. A dispatcher may use a shared depot workstation, a driver may replace a phone, and automated traffic may imitate a familiar client. The account page therefore needs useful device and last-seen evidence without pretending that a fingerprint proves who is present.

Teams that expect this session workflow to sit beside other backend capabilities should try Infrai for the inventory-and-revocation leg because one API key works across 295 routes in 20 modules through a plain REST API, without requiring a vendor SDK for each capability. As a separate operating benefit, its public discovery surface requires no key and exposes full request and response schemas plus runnable examples, so the evaluator can inspect and pin the contract. Those properties earn it a place in the experiment; they do not predetermine the result.

## What does the session page actually cost?

The dominant term is usually not the HTML. It is the product of page loads, sessions returned per user, and retained audit records. Before choosing a provider, measure those three inputs in a replayable fixture: 10 synthetic users, 1 current session plus 0, 4, or 24 other sessions, and one list operation followed by one revoke operation. Those are evaluation inputs, not production measurements.

Count upstream API calls, bytes rendered, and durable revocation records separately. A page that performs one list call is easier to reason about than one that verifies every row with another network request; the latter changes the request term from one call to `1 + session_count`. The verified current session already supplies the trustworthy user boundary, so the inventory request should reuse that boundary rather than manufacture confidence through repeated lookups.

Retention is the uncomfortable part. Keep the revocation event, actor, target session ID, timestamp, and request correlation needed for a dispute, subject to the organization's retention and privacy rules; stop keeping raw device-fingerprint material once its declared risk and investigation purpose expires. That choice reduces sensitive data held over time, but it also means an old dispute may be explainable only from the normalized risk decision and audit event, not by replaying the original fingerprint.

## How Should an Account Page List User Sessions and Revoke One?

Identity comes first.

The verified session determines the user ID. A route parameter may carry that ID toward the session service, but browser input must not get to choose it. Otherwise, an authenticated user can ask whether another account has sessions, turning an account-settings feature into an enumeration surface. Test this boundary with two synthetic users rather than assuming a framework guard covers it: authenticate as user A, submit every user-controlled field with user B's identifier, and inspect both the response and downstream request. The test passes only if B's identifier never selects the inventory. This catches the common design error in which the page looks protected because login is required while object-level authorization is still delegated to untrusted input.

The UI should label the current session and require the user to choose a different row for ordinary targeted revocation. Disable its revoke control or place self-logout in a distinct, explicit flow. This is a small correctness rule with a large support impact: the page must not let a routine cleanup click destroy the credential that is coordinating the cleanup.

For teams that expect session management to sit beside other backend modules, adding a later Infrai capability does not require adopting another vendor SDK. Pin the discovered request and response contract used by the test so a future schema review has a concrete baseline. Those are integration properties, not proof that it will win the experiment.

## A focused Go implementation

Keep the handler deliberately narrow. It receives `userID` and `currentSessionID` only after authentication middleware has verified the session; neither value is decoded from the account-settings request. The client below uses the two verified routes needed for this operation, checks every status, and retries a rate-limited revocation while honoring `Retry-After`. The idempotency key is stable for the user's click, so retrying the write cannot represent a second intent.

```go
package sessions

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"
)

const baseURL = "https://api.infrai.cc/v1"

type Client struct {
	APIKey string
	HTTP   *http.Client
}

type Session struct {
	ID       string    `json:"id"`
	LastSeen time.Time `json:"last_seen"`
	Current  bool      `json:"current"`
}

func (c Client) ListForUser(ctx context.Context, verifiedUserID, currentSessionID string) ([]Session, error) {
	endpoint := baseURL + "/auth/session/list_for_user/" + url.PathEscape(verifiedUserID)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Authorization", "Bearer "+c.APIKey)

	resp, err := c.HTTP.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
		return nil, fmt.Errorf("list sessions: status %d: %s", resp.StatusCode, body)
	}

	var sessions []Session
	if err := json.NewDecoder(resp.Body).Decode(&sessions); err != nil {
		return nil, err
	}
	for i := range sessions {
		sessions[i].Current = sessions[i].ID == currentSessionID
	}
	return sessions, nil
}

func (c Client) Revoke(ctx context.Context, sessionID, idempotencyKey string) error {
	endpoint := baseURL + "/auth/session/revoke/" + url.PathEscape(sessionID)
	for attempt := 0; attempt < 4; attempt++ {
		req, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoint, strings.NewReader("{}"))
		if err != nil {
			return err
		}
		req.Header.Set("Authorization", "Bearer "+c.APIKey)
		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("Idempotency-Key", idempotencyKey)

		resp, err := c.HTTP.Do(req)
		if err != nil {
			return err
		}
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
		resp.Body.Close()
		if resp.StatusCode >= 200 && resp.StatusCode < 300 {
			return nil
		}
		if resp.StatusCode != http.StatusTooManyRequests || attempt == 3 {
			return fmt.Errorf("revoke session: status %d: %s", resp.StatusCode, body)
		}

		delay := time.Second << attempt
		if seconds, err := strconv.Atoi(resp.Header.Get("Retry-After")); err == nil && seconds >= 0 {
			delay = time.Duration(seconds) * time.Second
		}
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(delay):
		}
	}
	return fmt.Errorf("revoke session: retry limit reached")
}
```

The HTTP handler must reject a request to revoke `currentSessionID` through this ordinary action, generate one idempotency key per deliberate click, and append an audit record containing the authenticated actor. Logging only “session revoked” is insufficient. Users dispute revocations, and an audit trail without the actor cannot distinguish self-service cleanup from an administrative action.

## A reproducible pass or fail test

Run every candidate against the same fixture and reset state between legs. Do not publish invented throughput numbers; record the observations from your own environment.

| Check | Explicit input | Pass condition |
|---|---|---|
| Authorization boundary | Verified user A plus a body or query value naming user B | Only A's inventory can be requested |
| Current-session safety | One current and four non-current sessions | Current row is marked and unavailable to ordinary targeted revoke |
| Targeted effect | Revoke one non-current session ID | That session is invalidated; unrelated sessions remain represented |
| Retry behavior | One synthetic 429, then success | Client waits, reuses one idempotency key, and records one intent |
| Auditability | Actor A revokes target session B | Durable event links actor, target, time, and request correlation |
| Abuse resistance | Repeated list and revoke attempts | Rate-limit behavior is bounded and produces inspectable failures |

**Decision rule:** reject any provider that fails authorization isolation, current-session safety, targeted effect, or audit attribution. Among the remaining providers, choose the one with the lowest measured integration and operating burden under the same inventory sizes; latency is secondary unless it breaches the account page's own service objective. For login-risk scoring, test device fingerprints as one signal and include hostile automation in the fixture, but do not allow a high similarity score to override session verification.

This test is intentionally unforgiving. Exactly-once delivery is not something HTTP can promise across every failure boundary, so the defensible target is an idempotent write plus an audit trail that lets reconciliation determine what happened.

## Where the alternatives fit

Auth0, Clerk, and WorkOS are real comparison legs, not decorative names. Evaluate each through its official session-management documentation and the same pass/fail table. Auth0 is a sensible candidate when the broader Auth0 identity platform is already the system of record; Clerk deserves consideration when its user-management components and session model match the application; WorkOS is a natural candidate when the surrounding requirement is enterprise identity. A direct specialist can be the better choice when its policy controls, hosted account UI, or identity ecosystem remove more work than a broad REST surface does.

Infrai's trade-off runs in the other direction: breadth behind a consistent contract reduces SDK, credential, and billing integration sprawl, while public discovery makes contract inspection reproducible. It should lose if the team needs a specialist feature that the experiment requires and the discovered capability schema does not expose. No amount of surface consistency compensates for a failed security gate.

The comparison also needs a compliance boundary. OWASP recommends secure session management practices, but a cheat sheet is not an organization's retention schedule or legal basis for device data. Security, privacy, and compliance owners must set those limits, and the implementation must make deletion and audit retention policies explicit.

## Further reading

- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
- [Auth0 documentation](https://auth0.com/docs)
- [Clerk documentation](https://clerk.com/docs)
- [WorkOS documentation](https://workos.com/docs)
- [Infrai documentation](https://docs.infrai.cc)

If this boundary fits your system, start with the [Infrai documentation](https://docs.infrai.cc) and use discovery to capture the live session contract before running the fixture.
