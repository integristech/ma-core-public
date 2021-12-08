/*
    Mango - Open Source M2M - http://mango.serotoninsoftware.com
    Copyright (C) 2006-2011 Serotonin Software Technologies Inc.
    @author Matthew Lohbihler

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General License for more details.

    You should have received a copy of the GNU General License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.serotonin.m2m2.db.dao;

import java.time.Period;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.codahale.metrics.Meter;
import com.infiniteautomation.mango.db.iterators.MergingIterator;
import com.infiniteautomation.mango.db.iterators.PointValueIterator;
import com.infiniteautomation.mango.db.query.CountingConsumer;
import com.infiniteautomation.mango.db.query.SingleValueConsumer;
import com.infiniteautomation.mango.db.query.WideCallback;
import com.serotonin.m2m2.rt.dataImage.IdPointValueTime;
import com.serotonin.m2m2.rt.dataImage.PointValueTime;
import com.serotonin.m2m2.vo.DataPointVO;
import com.serotonin.m2m2.vo.bean.PointHistoryCount;

public interface PointValueDao {

    static void validateLimit(Integer limit) {
        if (limit != null) {
            if (limit < 0) {
                throw new IllegalArgumentException("Limit may not be negative");
            }
        }
    }

    static void validateChunkSize(int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Chunk size must be greater than zero");
        }
    }

    static void validateTimePeriod(Long from, Long to) {
        if (from != null && to != null && to < from) {
            throw new IllegalArgumentException("To time must be greater than or equal to from time");
        }
    }

    static void validateNotNull(Object argument) {
        if (argument == null) {
            throw new IllegalArgumentException("Argument can't be null");
        }
    }

    enum TimeOrder {
        /**
         * Ascending time order, i.e. oldest values first
         */
        ASCENDING(Comparator.comparingLong(IdPointValueTime::getTime)),

        /**
         * Descending time order, i.e. newest values first
         */
        DESCENDING(Comparator.comparingLong(IdPointValueTime::getTime).reversed());


        private final Comparator<IdPointValueTime> comparator;

        TimeOrder(Comparator<IdPointValueTime> comparator) {
            this.comparator = comparator;
        }

        public Comparator<IdPointValueTime> getComparator() {
            return comparator;
        }
    }

    class StartAndEndTime {
        private final long startTime;
        private final long endTime;

        public StartAndEndTime(long startTime, long endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public long getStartTime() {
            return startTime;
        }

        public long getEndTime() {
            return endTime;
        }
    }

    /**
     * Save a stream of point values synchronously i.e. immediately.
     * This method blocks until all elements in the stream are consumed.
     *
     * @param pointValues stream of values to save
     * @throws IllegalArgumentException if pointValues is null
     */
    default void savePointValues(Stream<? extends BatchPointValue> pointValues) {
        savePointValues(pointValues, chunkSize());
    }

    /**
     * Save a stream of point values synchronously i.e. immediately.
     * This method blocks until all elements in the stream are consumed.
     *
     * @param pointValues stream of values to save
     * @param chunkSize chunk or batch size to save at once, this may be ignored by databases which support streams natively
     * @throws IllegalArgumentException if pointValues is null
     */
    default void savePointValues(Stream<? extends BatchPointValue> pointValues, int chunkSize) {
        PointValueDao.validateNotNull(pointValues);
        PointValueDao.validateChunkSize(chunkSize);
        pointValues.forEach(v -> savePointValueSync(v.getVo(), v.getPointValue()));
    }

    /**
     * Save a point value synchronously i.e. immediately.
     *
     * <p>If the point value implements {@link com.serotonin.m2m2.rt.dataImage.IAnnotated IAnnotated}
     * e.g. {@link com.serotonin.m2m2.rt.dataImage.AnnotatedPointValueTime AnnotatedPointValueTime}, then the
     * annotation should also be stored in the database.</p>
     *
     * @param vo data point
     * @param pointValue value to save for point (may be annotated)
     * @throws IllegalArgumentException if vo or pointValue are null
     */
    PointValueTime savePointValueSync(DataPointVO vo, PointValueTime pointValue);

    /**
     * Save a point value asynchronously i.e. delayed.
     * The implementation may batch and save point values at a later time.
     *
     * <p>If the point value implements {@link com.serotonin.m2m2.rt.dataImage.IAnnotated IAnnotated}
     * e.g. {@link com.serotonin.m2m2.rt.dataImage.AnnotatedPointValueTime AnnotatedPointValueTime}, then the
     * annotation should also be stored in the database.</p>
     *
     * @param vo data point
     * @param pointValue value to save for point (may be annotated)
     * @throws IllegalArgumentException if vo or pointValue are null
     */
    void savePointValueAsync(DataPointVO vo, PointValueTime pointValue);

    /**
     * Flushes all queued/batched point values (saved via {@link #savePointValueAsync})
     * out to the database. Blocks until complete.
     * Should only be used for tests and benchmarking.
     */
    default void flushPointValues() {
        // no-op
    }

    /**
     * Get the latest point values for a single point, with a limit.
     * Values are returned in descending time order, i.e. newest values first.
     *
     * @param vo data point
     * @param limit maximum number of values to return
     * @return list of point values, in descending time order, i.e. the newest value first.
     * @throws IllegalArgumentException if vo is null, if limit is negative
     */
    default List<PointValueTime> getLatestPointValues(DataPointVO vo, int limit) {
        PointValueDao.validateNotNull(vo);
        List<PointValueTime> values = new ArrayList<>(limit);
        getPointValuesPerPoint(Collections.singleton(vo), null, null, limit, TimeOrder.DESCENDING, (Consumer<? super IdPointValueTime>) values::add);
        return values;
    }

    /**
     * Get the latest point values for a single point, for the time range {@code [-∞,to)} with a limit.
     * Values are returned in descending time order, i.e. the newest value first.
     *
     * @param vo data point
     * @param to to time (epoch ms), exclusive
     * @param limit maximum number of values to return
     * @return list of point values, in descending time order, i.e. the newest value first.
     * @throws IllegalArgumentException if vo is null, if limit is negative
     */
    default List<PointValueTime> getLatestPointValues(DataPointVO vo, long to, int limit) {
        PointValueDao.validateNotNull(vo);
        List<PointValueTime> values = new ArrayList<>(limit);
        getPointValuesPerPoint(Collections.singleton(vo), null, to, limit, TimeOrder.DESCENDING, (Consumer<? super IdPointValueTime>) values::add);
        return values;
    }

    /**
     * Get the latest point value for a single point.
     *
     * @param vo data point
     * @return the latest point value, i.e. the newest value.
     * @throws IllegalArgumentException if vo is null
     */
    default Optional<PointValueTime> getLatestPointValue(DataPointVO vo) {
        PointValueDao.validateNotNull(vo);
        SingleValueConsumer<PointValueTime> holder = new SingleValueConsumer<>();
        getPointValuesPerPoint(Collections.singleton(vo), null, Long.MAX_VALUE, 1, TimeOrder.DESCENDING, holder);
        return holder.getValue();
    }

    /**
     * Get the point value prior to the given time, for a single point.
     *
     * @param vo data point
     * @param time the time (epoch ms), exclusive
     * @return the point value prior to the given time
     * @throws IllegalArgumentException if vo is null
     */
    default Optional<PointValueTime> getPointValueBefore(DataPointVO vo, long time) {
        PointValueDao.validateNotNull(vo);
        SingleValueConsumer<PointValueTime> holder = new SingleValueConsumer<>();
        getPointValuesPerPoint(Collections.singleton(vo), null, time, 1, TimeOrder.DESCENDING, holder);
        return holder.getValue();
    }

    /**
     * Get the point value at, or after the given time, for a single point.
     *
     * @param vo data point
     * @param time the time (epoch ms), inclusive
     * @return the point value at, or after the given time
     * @throws IllegalArgumentException if vo is null
     */
    default Optional<PointValueTime> getPointValueAfter(DataPointVO vo, long time) {
        PointValueDao.validateNotNull(vo);
        SingleValueConsumer<PointValueTime> holder = new SingleValueConsumer<>();
        getPointValuesPerPoint(Collections.singleton(vo), time, null, 1, TimeOrder.ASCENDING, holder);
        return holder.getValue();
    }

    /**
     * Get the point value at exactly the given time, for a single point.
     *
     * @param vo data point
     * @param time the time (epoch ms)
     * @return the point value exactly at the given time
     * @throws IllegalArgumentException if vo is null
     */
    default Optional<PointValueTime> getPointValueAt(DataPointVO vo, long time) {
        PointValueDao.validateNotNull(vo);
        SingleValueConsumer<PointValueTime> holder = new SingleValueConsumer<>();
        getPointValuesPerPoint(Collections.singleton(vo), time, time, 1, TimeOrder.ASCENDING, holder);
        return holder.getValue();
    }

    /**
     * Get point values for a single point, for the time range {@code [from,∞)}.
     *
     * @param vo data point
     * @param from from time (epoch ms), inclusive
     * @return list of point values, in ascending time order, i.e. the oldest value first.
     * @throws IllegalArgumentException if vo is null
     */
    default List<PointValueTime> getPointValues(DataPointVO vo, long from) {
        PointValueDao.validateNotNull(vo);
        List<PointValueTime> values = new ArrayList<>();
        getPointValuesPerPoint(Collections.singleton(vo), from, null, null, TimeOrder.ASCENDING, (Consumer<? super IdPointValueTime>) values::add);
        return values;
    }

    /**
     * Get point values for a single point, for the time range {@code [from,to)}.
     *
     * @param vo data point
     * @param from from time (epoch ms), inclusive
     * @param to to time (epoch ms), exclusive
     * @return list of point values, in ascending time order, i.e. the oldest value first.
     * @throws IllegalArgumentException if vo is null
     */
    default List<PointValueTime> getPointValuesBetween(DataPointVO vo, long from, long to) {
        PointValueDao.validateNotNull(vo);
        PointValueDao.validateTimePeriod(from, to);
        List<PointValueTime> values = new ArrayList<>();
        getPointValuesPerPoint(Collections.singleton(vo), from, to, null, TimeOrder.ASCENDING, (Consumer<? super IdPointValueTime>) values::add);
        return values;
    }

    /**
     * Get point values for a single point, for the time range {@code [from,to)}.
     *
     * @param vo data point
     * @param from from time (epoch ms), inclusive
     * @param to to time (epoch ms), exclusive
     * @param callback callback to return point values, in ascending time order, i.e. the oldest value first.
     * @throws IllegalArgumentException if vo or callback are null
     */
    default void getPointValuesBetween(DataPointVO vo, long from, long to, Consumer<? super PointValueTime> callback) {
        PointValueDao.validateNotNull(vo);
        PointValueDao.validateTimePeriod(from, to);
        PointValueDao.validateNotNull(callback);
        getPointValuesPerPoint(Collections.singleton(vo), from, to, null, TimeOrder.ASCENDING, callback);
    }

    /**
     * Get the point values for a collection of points, for the time range {@code [from,to)}.
     *
     * @param vos data points
     * @param from from time (epoch ms), inclusive
     * @param to to time (epoch ms), exclusive
     * @param callback callback to return point values, in ascending time order, i.e. the oldest value first.
     * @throws IllegalArgumentException if vo or callback are null
     */
    default void getPointValuesBetween(Collection<? extends DataPointVO> vos, long from, long to, Consumer<? super IdPointValueTime> callback) {
        PointValueDao.validateNotNull(vos);
        PointValueDao.validateTimePeriod(from, to);
        PointValueDao.validateNotNull(callback);
        getPointValuesPerPoint(vos, from, to, null, TimeOrder.ASCENDING, callback);
    }

    /**
     * Get the point values for a collection of points, for the time range {@code [from,to)} with a limit.
     * Values are grouped by point, and returned (via callback) in either ascending or descending time order.
     *
     * <p>The order in which points are grouped and values are returned may not match the order of the passed in
     * collection, but is generally in order of the data point's seriesId.</p>
     *
     * @param vos data points
     * @param from from time (epoch ms), inclusive
     * @param to to time (epoch ms), exclusive
     * @param limit maximum number of values to return per point (if null, no limit is applied)
     * @param sortOrder time order in which to return point values
     * @param callback callback to return point values, in ascending time order, i.e. the oldest value first.
     * @throws IllegalArgumentException if vo or callback are null, if limit is negative, if to is less than from
     */
    void getPointValuesPerPoint(Collection<? extends DataPointVO> vos, @Nullable Long from, @Nullable Long to, @Nullable Integer limit, TimeOrder sortOrder, Consumer<? super IdPointValueTime> callback);

    /**
     * Get the point values for a collection of points, for the time range {@code [from,to)} with a limit.
     * Values are returned (via callback) in either ascending or descending time order.
     *
     * @param vos data points
     * @param from from time (epoch ms), inclusive
     * @param to to time (epoch ms), exclusive
     * @param limit maximum number of values to return (if null, no limit is applied)
     * @param sortOrder time order in which to return point values
     * @param callback callback to return point values, in ascending time order, i.e. the oldest value first.
     * @throws IllegalArgumentException if vo or callback are null, if limit is negative, if to is less than from
     */
    default void getPointValuesCombined(Collection<? extends DataPointVO> vos, @Nullable Long from, @Nullable Long to, @Nullable Integer limit, TimeOrder sortOrder, Consumer<? super IdPointValueTime> callback) {
        PointValueDao.validateNotNull(vos);
        PointValueDao.validateTimePeriod(from, to);
        PointValueDao.validateLimit(limit);
        PointValueDao.validateNotNull(sortOrder);
        PointValueDao.validateNotNull(callback);
        if (vos.isEmpty() || limit != null && limit == 0) return;

        int minChunkSize = 10;
        int maxChunkSize = chunkSize();
        // take a guess at a good chunk size to use based on number of points and total limit
        int chunkSize = limit == null ? maxChunkSize : Math.max(Math.min(limit / vos.size() + 1, maxChunkSize), minChunkSize);

        List<PointValueIterator> iterators = vos.stream()
                .map(p -> new PointValueIterator(this, p, from, to, chunkSize, sortOrder))
                .collect(Collectors.toList());
        Comparator<IdPointValueTime> comparator = sortOrder.getComparator().thenComparingInt(IdPointValueTime::getSeriesId);
        MergingIterator<IdPointValueTime> mergingIterator = new MergingIterator<>(iterators, comparator);

        for (int i = 0; (limit == null || i < limit) && mergingIterator.hasNext(); i++) {
            callback.accept(mergingIterator.next());
        }
    }

    /**
     * @see #streamPointValues(DataPointVO, Long, Long, Integer, TimeOrder, int)
     */
    default Stream<IdPointValueTime> streamPointValues(DataPointVO vo, @Nullable Long from, @Nullable Long to,
                                                       @Nullable Integer limit, TimeOrder sortOrder) {
        return streamPointValues(vo, from, to, limit, sortOrder, chunkSize());
    }

    /**
     * Stream the point values for a single point, for the time range {@code [from,to)}.
     * Values are streamed in either ascending or descending time order.
     *
     * <p>The values should be lazily fetched from the underlying database. If this is not supported, the values should be
     * pre-fetched in chunks of size {@link #chunkSize()} and buffered out.</p>
     *
     * <p>The limit can often be omitted, as it is only useful for implementations which pre-fetch and buffer
     * with small limits (i.e. less than the {@link #chunkSize()}).</p>
     *
     * <p>The returned {@link Stream} <strong>must</strong> be closed, use a try-with-resources block.</p>
     * <pre>{@code
     * try (var stream = streamPointValues(point, from, to, null, ASCENDING)) {
     *     // use stream
     * }
     * }</pre>
     *
     * @param vo the data point
     * @param from from time (epoch ms), inclusive
     * @param to to time (epoch ms), exclusive
     * @param limit maximum number of values to return (if null, no limit is applied)
     * @param sortOrder time order in which to return point values
     * @throws IllegalArgumentException if vo is null, if to is less than from
     */
    default Stream<IdPointValueTime> streamPointValues(DataPointVO vo, @Nullable Long from, @Nullable Long to,
                                                       @Nullable Integer limit, TimeOrder sortOrder, int chunkSize) {
        PointValueDao.validateNotNull(vo);
        PointValueDao.validateTimePeriod(from, to);
        PointValueDao.validateLimit(limit);
        PointValueDao.validateNotNull(sortOrder);
        PointValueDao.validateChunkSize(chunkSize);

        if (limit != null) {
            chunkSize = Math.min(limit, chunkSize);
        }
        PointValueIterator it = new PointValueIterator(this, vo, from, to, chunkSize, sortOrder);
        Spliterator<IdPointValueTime> spliterator = Spliterators.spliteratorUnknownSize(it,
                Spliterator.ORDERED | Spliterator.NONNULL | Spliterator.DISTINCT | Spliterator.SORTED);
        return StreamSupport.stream(spliterator, false);
    }

    /**
     * Stream the point values for a collection of points, for the time range {@code [from,to)}.
     * Values are grouped by point, and streamed in either ascending or descending time order.
     *
     * <p>The order in which points are grouped and values are returned may not match the order of the passed in
     * collection, but is generally in order of the data point's seriesId.</p>
     *
     * <p>The values should be lazily fetched from the underlying database. If this is not supported, the values should be
     * pre-fetched in chunks of size {@link #chunkSize()} and buffered out.</p>
     *
     * <p>The limit can often be omitted, as it is only useful for implementations which pre-fetch and buffer
     * with small limits (i.e. less than the {@link #chunkSize()}).</p>
     *
     * <p>The returned {@link Stream} <strong>must</strong> be closed, use a try-with-resources block.</p>
     * <pre>{@code
     * try (var stream = streamPointValuesPerPoint(point, from, to, null, ASCENDING)) {
     *     // use stream
     * }
     * }</pre>
     *
     * @param vos data points
     * @param from from time (epoch ms), inclusive
     * @param to to time (epoch ms), exclusive
     * @param limit maximum number of values to return per point (if null, no limit is applied)
     * @param sortOrder time order in which to return point values
     * @throws IllegalArgumentException if vo is null, if to is less than from
     */
    default Stream<IdPointValueTime> streamPointValuesPerPoint(Collection<? extends DataPointVO> vos,
                                                               @Nullable Long from, @Nullable Long to,
                                                               @Nullable Integer limit, TimeOrder sortOrder) {
        return vos.stream().flatMap(vo -> streamPointValues(vo, from, to, limit, sortOrder));
    }

    /**
     * Stream the point values for a collection of points, for the time range {@code [from,to)}.
     * Values are streamed in either ascending or descending time order.
     *
     * <p>The values should be lazily fetched from the underlying database. If this is not supported, the values should be
     * pre-fetched in chunks of size {@link #chunkSize()} and buffered out.</p>
     *
     * <p>The limit can often be omitted, as it is only useful for implementations which pre-fetch and buffer
     * with small limits (i.e. less than the {@link #chunkSize()}).</p>
     *
     * <p>The returned {@link Stream} <strong>must</strong> be closed, use a try-with-resources block.</p>
     * <pre>{@code
     * try (var stream = streamPointValuesCombined(point, from, to, null, ASCENDING)) {
     *     // use stream
     * }
     * }</pre>
     *
     * @param vos data points
     * @param from from time (epoch ms), inclusive
     * @param to to time (epoch ms), exclusive
     * @param limit maximum number of values to return (if null, no limit is applied)
     * @param sortOrder time order in which to return point values
     * @throws IllegalArgumentException if vo is null, if to is less than from
     */
    default Stream<IdPointValueTime> streamPointValuesCombined(Collection<? extends DataPointVO> vos,
                                                               @Nullable Long from, @Nullable Long to,
                                                               @Nullable Integer limit, TimeOrder sortOrder) {
        Comparator<IdPointValueTime> comparator = sortOrder.getComparator().thenComparingInt(IdPointValueTime::getSeriesId);
        var streams = vos.stream()
                // limit is a total limit, but may as well limit per point
                // e.g. if user supplies a limit of 1, we may as well only retrieve a max of 1 per point
                .map(vo -> streamPointValues(vo, from, to, limit, sortOrder))
                .collect(Collectors.toList());
        var result = MergingIterator.mergeStreams(streams, comparator);
        return limit != null ? result.limit(limit) : result;
    }

    /**
     * Get the point values for a collection of points, for the time range {@code [from,to)}, while also
     * returning the value immediately prior and after the given time range.
     *
     * This query facilitates charting of values, where for continuity in the chart the values immediately
     * before and after the time range are required.
     *
     * NOTE: The preQuery and postQuery callback methods are only called if there is data before/after the time range.
     *
     * @param vo data point
     * @param from from time (epoch ms), inclusive
     * @param to to time (epoch ms), exclusive
     * @param callback callback to return point values, in ascending time order, i.e. the oldest value first.
     * @throws IllegalArgumentException if vo or callback are null, if to is less than from
     */
    default void wideQuery(DataPointVO vo, long from, long to, WideCallback<? super PointValueTime> callback) {
        PointValueDao.validateNotNull(vo);
        PointValueDao.validateNotNull(callback);
        PointValueDao.validateTimePeriod(from, to);

        getPointValueBefore(vo, from).ifPresent(callback::firstValue);
        getPointValuesBetween(vo, from, to, callback);
        getPointValueAfter(vo, to).ifPresent(callback::lastValue);
    }

    /**
     * Retrieve the initial value for a set of points. That is, the value immediately prior to, or exactly at the given timestamp.
     * The returned map is guaranteed to contain an entry for every point, however the value may be null.
     *
     * @param vos data points
     * @param time timestamp (epoch ms) to get the value at, inclusive
     * @return map of seriesId to point value
     */
    default Map<Integer, IdPointValueTime> initialValues(Collection<? extends DataPointVO> vos, long time) {
        PointValueDao.validateNotNull(vos);
        if (vos.isEmpty()) return Collections.emptyMap();

        Map<Integer, IdPointValueTime> values = new HashMap<>(vos.size());
        getPointValuesPerPoint(vos, null, time + 1, 1, TimeOrder.DESCENDING, v -> values.put(v.getSeriesId(), v));
        for (DataPointVO vo : vos) {
            values.computeIfAbsent(vo.getSeriesId(), seriesId -> new IdPointValueTime(seriesId, null, time));
        }
        return values;
    }

    /**
     * Get the point values for a collection of points, for the time range {@code [from,to)} with a limit.
     * Also notifies the callback of each point's value at the start and end of the time range ("bookend" values), for ease of charting.
     * Values are grouped by point, and returned (via callback) in ascending time order, i.e. the oldest value first.
     *
     * <p>The order in which points are grouped and values are returned may not match the order of the passed in
     * collection, but is generally in order of the data point's seriesId.</p>
     *
     * <p>The callback's firstValue and lastValue method will always be called for each point, the value however
     * may be null.</p>
     *
     * @param vos data points
     * @param from from time (epoch ms), inclusive
     * @param to to time (epoch ms), exclusive
     * @param limit maximum number of values to return per point (if null, no limit is applied). The limit does not apply to the "bookend" values.
     * @param callback callback to return point values, in ascending time order, i.e. the oldest value first.
     * @throws IllegalArgumentException if vo or callback are null, if limit is negative, if to is less than from
     */
    default void wideBookendQueryPerPoint(Collection<? extends DataPointVO> vos, long from, long to, @Nullable Integer limit, WideCallback<? super IdPointValueTime> callback) {
        PointValueDao.validateNotNull(vos);
        PointValueDao.validateTimePeriod(from, to);
        PointValueDao.validateLimit(limit);
        PointValueDao.validateNotNull(callback);
        if (vos.isEmpty()) return;

        Map<Integer, IdPointValueTime> values = initialValues(vos, from);

        for (DataPointVO vo : vos) {
            var first = values.get(vo.getSeriesId());
            callback.firstValue(first.withNewTime(from), first.getValue() == null || first.getTime() != from);
            getPointValuesPerPoint(Collections.singleton(vo), from, to, limit, TimeOrder.ASCENDING, v -> {
                var previousValue = Objects.requireNonNull(values.put(v.getSeriesId(), v));
                // so we don't call row() for same value that was passed to firstValue()
                if (v.getTime() > previousValue.getTime()) {
                    callback.accept(v);
                }
            });
            callback.lastValue(values.get(vo.getSeriesId()).withNewTime(to), true);
        }
    }

    /**
     * Get the point values for a collection of points, for the time range {@code [from,to)} with a limit.
     * Also notifies the callback of each point's value at the start and end of the time range ("bookend" values), for ease of charting.
     * Values are returned (via callback) in ascending time order, i.e. the oldest value first.
     *
     * <p>The callback's firstValue and lastValue method will always be called for each point, the value however
     * may be null.</p>
     *
     * @param vos data points
     * @param from from time (epoch ms), inclusive
     * @param to to time (epoch ms), exclusive
     * @param limit maximum number of values to return (if null, no limit is applied). The limit does not apply to the "bookend" values.
     * @param callback callback to return point values, in ascending time order, i.e. the oldest value first.
     * @throws IllegalArgumentException if vo or callback are null, if limit is negative, if to is less than from
     */
    default void wideBookendQueryCombined(Collection<? extends DataPointVO> vos, long from, long to, @Nullable Integer limit, WideCallback<? super IdPointValueTime> callback) {
        PointValueDao.validateNotNull(vos);
        PointValueDao.validateTimePeriod(from, to);
        PointValueDao.validateLimit(limit);
        PointValueDao.validateNotNull(callback);
        if (vos.isEmpty()) return;

        Map<Integer, IdPointValueTime> values = initialValues(vos, from);

        for (IdPointValueTime value : values.values()) {
            callback.firstValue(value.withNewTime(from), value.getValue() == null || value.getTime() != from);
        }
        getPointValuesCombined(vos, from, to, limit, TimeOrder.ASCENDING, value -> {
            var previousValue = Objects.requireNonNull(values.put(value.getSeriesId(), value));
            // so we don't call row() for same value that was passed to firstValue()
            if (value.getTime() > previousValue.getTime()) {
                callback.accept(value);
            }
        });
        for (IdPointValueTime value : values.values()) {
            callback.lastValue(value.withNewTime(to), true);
        }
    }

    /**
     * Enable or disable the <strong>per point</strong> nightly purge of point values.
     * Typically, this setting should be disabled if:
     * <ul>
     *     <li>The database supports retention policies</li>
     *     <li>The database is inefficient at deleting values on a per-series basis</li>
     * </ul>
     *
     * <p>If per-point purge is disabled or the {@link #deletePointValuesBefore(com.serotonin.m2m2.vo.DataPointVO, long)}
     * method is not implemented, the data point and data source purge override settings will have no effect.</p>
     *
     * @return true to enable nightly purge of point values
     */
    default boolean enablePerPointPurge() {
        return true;
    }

    /**
     * Purge (delete) point values for all data points, for the time range {@code [-∞,endTime)}. This method is called
     * daily (typically at midnight) by the purge task. The implementation may choose to truncate the endTime to a
     * shard boundary but should never purge point values newer than endTime.
     *
     * <p>Typically, a database that supports retention policies should not implement this method,
     * i.e. throw an {@link UnsupportedOperationException}.</p>
     *
     * <p>This method is called regardless of the {@link #enablePerPointPurge()} setting.</p>
     *
     * @param endTime end of time range (epoch ms), exclusive
     * @return the number of point values deleted, return an empty optional if this will add additional overhead
     * @throws UnsupportedOperationException if the database does not support this operation
     */
    default Optional<Long> deletePointValuesBefore(long endTime) {
        throw new UnsupportedOperationException();
    }

    /**
     * Set a retention policy for the entire database. This method will be called after initialization and
     * whenever the point value purge settings are configured.
     *
     * @param period period for which to retain point values
     * @throws UnsupportedOperationException if this database does not support setting a retention policy
     */
    default void setRetentionPolicy(Period period) {
        throw new UnsupportedOperationException();
    }

    /**
     * Delete point values for a data point, for the time range {@code [startTime,endTime)}.
     * @param vo data point
     * @param startTime start of time range (epoch ms), inclusive
     * @param endTime end of time range (epoch ms), exclusive
     * @return the number of point values deleted, return an empty optional if this will add additional overhead
     * @throws UnsupportedOperationException if the database does not support delete
     * @throws IllegalArgumentException if vo is null
     */
    Optional<Long> deletePointValuesBetween(DataPointVO vo, @Nullable Long startTime, @Nullable Long endTime);

    /**
     * Delete point values for a data point, for the time range {@code [-∞,endTime)}.
     * @param vo data point
     * @param endTime end of time range (epoch ms), exclusive
     * @return the number of point values deleted, return an empty optional if this will add additional overhead
     * @throws UnsupportedOperationException if the database does not support delete
     * @throws IllegalArgumentException if vo is null
     */
    default Optional<Long> deletePointValuesBefore(DataPointVO vo, long endTime) {
        return deletePointValuesBetween(vo, null, endTime);
    }

    /**
     * Delete a point value for a data point at exactly the given time.
     *
     * @param vo data point
     * @param ts time (epoch ms) at which to delete point value
     * @return the number of point values deleted, return an empty optional if this will add additional overhead
     * @throws UnsupportedOperationException if the database does not support delete
     * @throws IllegalArgumentException if vo is null
     */
    default Optional<Long> deletePointValue(DataPointVO vo, long ts) {
        return deletePointValuesBetween(vo, ts, ts);
    }

    /**
     * Delete all point values for a data point, i.e. for the time range {@code [-∞,∞)}.
     * @param vo data point
     * @return the number of point values deleted, return an empty optional if this will add additional overhead
     * @throws UnsupportedOperationException if the database does not support delete
     * @throws IllegalArgumentException if vo is null
     */
    default Optional<Long> deletePointValues(DataPointVO vo) {
        return deletePointValuesBetween(vo, null, null);
    }

    /**
     * Delete all point values for all data points, i.e. for the time range {@code [-∞,∞)}.
     * @return the number of point values deleted, return an empty optional if this will add additional overhead
     * @throws UnsupportedOperationException if the database does not support delete
     * @throws IllegalArgumentException if vo is null
     */
    default Optional<Long> deleteAllPointData() {
        throw new UnsupportedOperationException();
    }

    /**
     * Delete any point values that are no longer tied to a point in the {@link com.infiniteautomation.mango.db.tables.DataPoints} table.
     * @return the number of point values deleted, return an empty optional if this will add additional overhead
     * @throws UnsupportedOperationException if the database does not support delete
     */
    Optional<Long> deleteOrphanedPointValues();

    /**
     * Count the number of point values for a point, for the time range {@code [from,to)}.
     *
     * @param vo data point
     * @param from from time (epoch ms), inclusive
     * @param to to time (epoch ms), exclusive
     * @return number of point values in the time range
     * @throws IllegalArgumentException if vo is null, if to is less than from
     */
    default long dateRangeCount(DataPointVO vo, @Nullable Long from, @Nullable Long to) {
        PointValueDao.validateNotNull(vo);
        PointValueDao.validateTimePeriod(from, to);
        CountingConsumer<PointValueTime> counter = new CountingConsumer<>();
        getPointValuesPerPoint(Collections.singleton(vo), from, to, null, TimeOrder.ASCENDING, counter);
        return counter.getCount();
    }

    /**
     * Get the time of the earliest recorded point value for this point.
     *
     * @param vo data point
     * @return timestamp (epoch ms) of the first point value recorded for the point
     * @throws IllegalArgumentException if vo is null
     */
    default Optional<Long> getInceptionDate(DataPointVO vo) {
        PointValueDao.validateNotNull(vo);
        return getStartTime(Collections.singleton(vo));
    }

    /**
     * Get the time of the earliest recorded point value for this collection of points.
     *
     * @param vos data points
     * @return timestamp (epoch ms) of the first point value recorded
     * @throws IllegalArgumentException if vos are null
     */
    default Optional<Long> getStartTime(Collection<? extends DataPointVO> vos) {
        PointValueDao.validateNotNull(vos);
        if (vos.isEmpty()) return Optional.empty();
        SingleValueConsumer<IdPointValueTime> consumer = new SingleValueConsumer<>();
        getPointValuesPerPoint(vos, null, null, 1, TimeOrder.ASCENDING, consumer);
        return consumer.getValue().map(PointValueTime::getTime);
    }

    /**
     * Get the time of the latest recorded point value for this collection of points.
     *
     * @param vos data points
     * @return timestamp (epoch ms) of the last point value recorded
     * @throws IllegalArgumentException if vos are null
     */
    default Optional<Long> getEndTime(Collection<? extends DataPointVO> vos) {
        PointValueDao.validateNotNull(vos);
        if (vos.isEmpty()) return Optional.empty();
        SingleValueConsumer<IdPointValueTime> consumer = new SingleValueConsumer<>();
        getPointValuesPerPoint(vos, null, null, 1, TimeOrder.DESCENDING, consumer);
        return consumer.getValue().map(PointValueTime::getTime);
    }

    /**
     * Get the earliest and latest timestamps for this collection of data points.
     *
     * @param vos data points
     * @return first and last timestamp (epoch ms) for the given set of points
     * @throws IllegalArgumentException if vos are null
     */
    default Optional<StartAndEndTime> getStartAndEndTime(Collection<? extends DataPointVO> vos) {
        PointValueDao.validateNotNull(vos);
        if (vos.isEmpty()) return Optional.empty();
        return getStartTime(vos).flatMap(startTime -> getEndTime(vos).map(endTime -> new StartAndEndTime(startTime, endTime)));
    }

    /**
     * @return number of point values to read/write at once when streaming data
     */
    default int chunkSize() {
        return 1000;
    }

    /**
     * Retrieves series with the most point values, sorted by the number of point values.
     *
     * @param limit max number of series to return
     * @return list of points and their value counts
     */
    default List<PointHistoryCount> topPointHistoryCounts(int limit) {
        throw new UnsupportedOperationException();
    }

    /**
     * Get the write-speed in values/second. Generally a 1-minute moving average is suitable, see
     * {@link Meter#getOneMinuteRate()}.
     *
     * @return number of point values written per second
     */
    double writeSpeed();

    /**
     * @return number of point values queued for insertion via {@link #savePointValueAsync(DataPointVO, PointValueTime)}
     * that have not been written yet.
     */
    long queueSize();

    /**
     * @return number of active batch writer threads processing the queue.
     */
    int threadCount();
}
