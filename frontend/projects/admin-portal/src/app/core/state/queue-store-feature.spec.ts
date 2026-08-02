import { TestBed } from '@angular/core/testing';
import { signalStore } from '@ngrx/signals';
import { withQueueEntities } from './queue-store-feature';

interface TestItem {
  readonly id: string;
}

const TestQueueStore = signalStore(withQueueEntities<TestItem>());

describe('withQueueEntities', () => {
  function setup() {
    TestBed.configureTestingModule({ providers: [TestQueueStore] });
    return TestBed.inject(TestQueueStore);
  }

  it('starts empty, not loading, with no error', () => {
    const store = setup();

    expect(store.items()).toEqual([]);
    expect(store.loading()).toBe(false);
    expect(store.loadError()).toBe(false);
    expect(store.isEmpty()).toBe(true);
  });

  it('setLoading clears a prior error and marks loading', () => {
    const store = setup();
    store.setLoadError();

    store.setLoading();

    expect(store.loading()).toBe(true);
    expect(store.loadError()).toBe(false);
    expect(store.isEmpty()).toBe(false); // loading, not genuinely empty
  });

  it('setItems stores the items and clears loading/error', () => {
    const store = setup();
    store.setLoading();

    store.setItems([{ id: 'a' }, { id: 'b' }]);

    expect(store.items()).toEqual([{ id: 'a' }, { id: 'b' }]);
    expect(store.loading()).toBe(false);
    expect(store.loadError()).toBe(false);
    expect(store.isEmpty()).toBe(false);
  });

  it('setItems([]) is a genuine empty state', () => {
    const store = setup();

    store.setItems([]);

    expect(store.isEmpty()).toBe(true);
  });

  it('setLoadError clears items and marks the error, not loading', () => {
    const store = setup();
    store.setItems([{ id: 'a' }]);

    store.setLoadError();

    expect(store.items()).toEqual([]);
    expect(store.loading()).toBe(false);
    expect(store.loadError()).toBe(true);
    expect(store.isEmpty()).toBe(false); // error state, not genuinely empty
  });
});
