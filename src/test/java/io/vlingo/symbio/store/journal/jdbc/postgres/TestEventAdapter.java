// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.symbio.store.journal.jdbc.postgres;

import io.vlingo.common.serialization.JsonSerialization;
import io.vlingo.symbio.EntryAdapter;
import io.vlingo.symbio.Metadata;
import io.vlingo.symbio.Entry.TextEntry;

public final class TestEventAdapter implements EntryAdapter<TestEvent,TextEntry> {
  @Override
  public TestEvent fromEntry(final TextEntry entry) {
    return JsonSerialization.deserialized(entry.entryData, TestEvent.class);
  }

  @Override
  public TextEntry toEntry(final TestEvent source) {
    return toEntry(source, source.id);
  }

  @Override
  public TextEntry toEntry(TestEvent source, String id) {
    final String serialization = JsonSerialization.serialized(source);
    return new TextEntry(TestEvent.class, 1, serialization, Metadata.nullMetadata());
  }
}
