package io.vlingo.symbio.store.state.jdbc.postgres.eventjournal;

import com.google.gson.Gson;
import io.vlingo.actors.Actor;
import io.vlingo.common.Completes;
import io.vlingo.symbio.Event;
import io.vlingo.symbio.Metadata;
import io.vlingo.symbio.State;
import io.vlingo.symbio.store.eventjournal.EventStream;
import io.vlingo.symbio.store.eventjournal.EventStreamReader;
import io.vlingo.symbio.store.state.jdbc.Configuration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;

public class PostgresEventStreamReader<T> extends Actor implements EventStreamReader<T> {
    private static final String QUERY_EVENTS =
            "SELECT id, event_data, event_metadata, event_type, event_type_version " +
                    "FROM vlingo_event_journal " +
                    "WHERE event_stream = ? AND event_offset >= ?";

    private static final String QUERY_SNAPSHOT =
            "SELECT snapshot_type, snapshot_type_version, snapshot_data, snapshot_data_version, snapshot_metadata " +
                    "FROM vlingo_event_journal_snapshots " +
                    "WHERE event_stream = ?";

    private final Connection connection;
    private final PreparedStatement queryEventsStatement;
    private final PreparedStatement queryLatestSnapshotStatement;
    private final Gson gson;

    public PostgresEventStreamReader(final Configuration configuration) throws SQLException {
        this.connection = configuration.connection;
        this.queryEventsStatement = this.connection.prepareStatement(QUERY_EVENTS);
        this.queryLatestSnapshotStatement = this.connection.prepareStatement(QUERY_SNAPSHOT);
        this.gson = new Gson();
    }

    @Override
    public Completes<EventStream<T>> streamFor(final String streamName) {
        return streamFor(streamName, 1);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Completes<EventStream<T>> streamFor(final String streamName, final int fromStreamVersion) {
        try {
            return completes().with(eventsFromOffset(streamName, fromStreamVersion));
        } catch (Exception e) {
            logger().log("vlingo/symbio-postgresql: " + e.getMessage(), e);
            return completes().with(new EventStream<>(streamName, 1, emptyList(), (State<T>) State.NullState.Text));
        }
    }

    @SuppressWarnings("unchecked")
    private EventStream<T> eventsFromOffset(final String streamName, final int offset) throws Exception {
        final State<T> snapshot = latestSnapshotOf(streamName);
        final List<Event<T>> events = new ArrayList<>();

        int dataVersion = offset;
        State<T> referenceSnapshot = (State<T>) State.NullState.Text;

        if (snapshot != State.NullState.Text) {
            if (snapshot.dataVersion > offset) {
                dataVersion = snapshot.dataVersion + 1;
                referenceSnapshot = snapshot;
            }
        }

        queryEventsStatement.setString(1, streamName);
        queryEventsStatement.setInt(2, dataVersion);
        final ResultSet resultSet = queryEventsStatement.executeQuery();
        while (resultSet.next()) {
            final String id = resultSet.getString(1);
            final String eventData = resultSet.getString(2);
            final String eventMetadata = resultSet.getString(3);
            final String eventType = resultSet.getString(4);
            final int eventTypeVersion = resultSet.getInt(5);

            final Class<T> classOfEvent = (Class<T>) Class.forName(eventType);
            final T eventDataDeserialized = gson.fromJson(eventData, classOfEvent);

            final Metadata eventMetadataDeserialized = gson.fromJson(eventMetadata, Metadata.class);

            events.add((Event<T>) new Event.ObjectEvent<>(id, classOfEvent, eventTypeVersion, eventDataDeserialized, eventMetadataDeserialized));
        }

        return new EventStream<>(streamName, dataVersion + events.size(), events, referenceSnapshot);
    }

    @SuppressWarnings("unchecked")
    private State<T> latestSnapshotOf(final String streamName) throws Exception {
        queryLatestSnapshotStatement.setString(1, streamName);
        final ResultSet resultSet = queryLatestSnapshotStatement.executeQuery();
        if (resultSet.next()) {
            final String snapshotDataType = resultSet.getString(1);
            final int snapshotTypeVersion = resultSet.getInt(2);
            final String snapshotData = resultSet.getString(3);
            final int snapshotDataVersion = resultSet.getInt(4);
            final String metadataJson = resultSet.getString(5);

            final Class<T> classOfEvent = (Class<T>) Class.forName(snapshotDataType);
            final T eventDataDeserialized = gson.fromJson(snapshotData, classOfEvent);

            final Metadata eventMetadataDeserialized = gson.fromJson(metadataJson, Metadata.class);

            return (State<T>) new State.ObjectState<>(streamName, classOfEvent, snapshotTypeVersion, eventDataDeserialized, snapshotDataVersion, eventMetadataDeserialized);
        }

        return (State<T>) State.NullState.Text;
    }
}