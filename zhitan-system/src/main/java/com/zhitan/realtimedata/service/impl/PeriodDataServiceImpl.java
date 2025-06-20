package com.zhitan.realtimedata.service.impl;

import com.zhitan.common.enums.Quality;
import com.zhitan.common.enums.StatisticType;
import com.zhitan.common.enums.TimeType;
import com.zhitan.common.utils.StringUtils;
import com.zhitan.common.utils.time.TimeManager;
import com.zhitan.model.domain.MeterPoint;
import com.zhitan.realtimedata.domain.*;
import com.zhitan.realtimedata.mapper.PeriodDataMapper;
import com.zhitan.model.service.IMeterPointService;
import com.zhitan.realtimedata.service.IPeriodDataService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class PeriodDataServiceImpl implements IPeriodDataService {
    private PeriodDataMapper periodDataMapper;
    private IMeterPointService energyIndexService;

    @Override
    public void save(EnergyUsed data) {
        String item = periodDataMapper.hasExist(data.getPointId(), data.getTimeCode());
        if (StringUtils.isEmpty(item)) {
            periodDataMapper.insert(data);
        } else {
            update(data);
        }
    }

    @Override
    public void saveTOUData(ElectricityDataDetail data) {
        periodDataMapper.insertTOUData(data);
    }

    @Override
    public void saveTOUData(List<ElectricityDataDetail> dataItems) {
        List<List<ElectricityDataDetail>> splitData = splitList(dataItems, 100);
        splitData.parallelStream().forEach(dataPart -> {
            try {
                periodDataMapper.insertTOUDataItems(dataPart);
            } catch (Exception ex) {
                log.error("批量保存数据失败", ex);
                dataPart.parallelStream().forEach(dataItem -> {
                    try {
                        periodDataMapper.insertTOUData(dataItem);
                    } catch (Exception singleEx) {
                        log.error("单个指标数据保存失败！【" + dataItem + "】", singleEx);
                    }
                });
            }
        });
    }

    @Override
    public void save(List<EnergyUsed> energyUseds) {
        List<List<EnergyUsed>> splitData = splitList(energyUseds, 100);
        splitData.parallelStream().forEach(dataPart -> {
            try {
                periodDataMapper.saveDataList(dataPart);
            } catch (Exception ex) {
                log.error("批量保存数据失败", ex);
                dataPart.parallelStream().forEach(dataItem -> {
                    try {
                        periodDataMapper.save(dataItem);
                    } catch (Exception singleEx) {
                        log.error("单个指标数据保存失败！【" + dataItem + "】", singleEx);
                    }
                });
            }
        });
    }

    @Override
    public void savePeriodData(List<EnergyUsed> energyUseds) {
        Map<String, List<String>> map = energyUseds.stream()
                .collect(Collectors.groupingBy(EnergyUsed::getTimeCode,
                        Collectors.mapping(EnergyUsed::getPointId, Collectors.toList())));
        List<String> keys = periodDataMapper.queryDataItemIsExist(map);
        List<EnergyUsed> insertData = energyUseds.stream()
                .filter(
                        item -> !keys.contains(String.format("%s:%s", item.getTimeCode(), item.getPointId())))
                .collect(Collectors.toList());
        if (!insertData.isEmpty()) {
            periodDataMapper.insertDataList(insertData);
        }

        List<EnergyUsed> updateData = energyUseds.stream()
                .filter(
                        item -> keys.contains(String.format("%s:%s", item.getTimeCode(), item.getPointId())))
                .collect(Collectors.toList());
        if (!updateData.isEmpty()) {
            periodDataMapper.saveDataList(updateData);
        }
    }

    @Override
    public void update(EnergyUsed data) {
        periodDataMapper.update(data);
    }

    @Override
    public EnergyUsed getDataByIndex(String indexId, String timeCode) {
        if (StringUtils.isEmpty(indexId)) {
            return null;
        }

        return periodDataMapper.getDataByIndex(indexId, timeCode);
    }

    @Override
    public EnergyUsed getDataByIndex(String indexId, Date dataTime, TimeType timeType) {
        if (StringUtils.isEmpty(indexId)) {
            return null;
        }

        String timeCode = TimeManager.getTimeCode(dataTime, timeType);
        return getDataByIndex(indexId, timeCode);
    }

    @Override
    public List<EnergyUsed> getDataItemsByIndex(List<String> indexIds, String timeCode) {
        if (indexIds != null && !indexIds.isEmpty()) {
            return periodDataMapper.getDatasByIndex(indexIds, timeCode);
        }

        return Collections.emptyList();
    }

    @Override
    public List<EnergyUsed> getDataItemsByIndex(List<String> indexIds, Date dataTime, TimeType timeType) {
        if (indexIds != null && !indexIds.isEmpty()) {
            String timeCode = TimeManager.getTimeCode(dataTime, timeType);
            return periodDataMapper.getDatasByIndex(indexIds, timeCode);
        }

        return Collections.emptyList();
    }

    @Override
    public List<EnergyUsed> getDataItemsByIndex(List<String> indexIds, Date beginTime,
                                                Date endTime, TimeType timeType) {
        if (indexIds != null && !indexIds.isEmpty()) {
            return periodDataMapper.getPeriodDatasByIndex(indexIds, beginTime, endTime, timeType);
        }

        return Collections.emptyList();
    }

    @Override
    public List<EnergyUsed> getDataItemsByIndex(String indexId,
                                                Date beginTime,
                                                Date endTime,
                                                TimeType timeType) {
        if (StringUtils.isEmpty(indexId)) {
            return Collections.emptyList();
        }

        List<String> indexIds = new ArrayList<>();
        indexIds.add(indexId);
        return periodDataMapper.getPeriodDatasByIndex(indexIds, beginTime, endTime, timeType);
    }

    @Override
    public List<EnergyUsed> getDataItemsByIndex(List<String> indexIds, Date beginTime, Date endTime,
                                                TimeType timeType, Quality quality) {
        if (indexIds != null && !indexIds.isEmpty()) {
            return periodDataMapper.getPeriodDatasByIndexAndQuality(indexIds,
                    beginTime,
                    endTime,
                    timeType,
                    quality);
        }

        return Collections.emptyList();
    }

    @Override
    public double dataStatisticsByIndex(String indexId,
                                        StatisticType statisticType,
                                        TimeType timeType,
                                        Date beginTime,
                                        Date endTime) {
        if (StringUtils.isEmpty(indexId)) {
            return 0;
        }

        Double value = periodDataMapper.dataStatisticsByIndex(
                indexId,
                statisticType,
                timeType,
                beginTime,
                endTime);

        return value == null ? 0 : value;
    }

    @Override
    public List<EnergyUsed> dataStatisticsByIndexes(List<String> indexIds, StatisticType
            statisticType, TimeType timeType, Date beginTime, Date endTime) {
        if (indexIds == null || indexIds.isEmpty()) {
            return Collections.emptyList();
        }

        return periodDataMapper.dataStatisticsByIndexs(
                indexIds,
                statisticType,
                timeType,
                beginTime,
                endTime);
    }

    @Override
    public double dataStatisticsByCode(String indexCode, StatisticType
            statisticType, TimeType timeType, Date beginTime, Date endTime) {
        if (StringUtils.isEmpty(indexCode)) {
            return 0;
        }

        List<String> indexCodes = Collections.singletonList(indexCode);
        List<EnergyUsed> energyUseds = dataStatisticsByCodes(
                indexCodes,
                statisticType,
                timeType,
                beginTime,
                endTime);
        if (!energyUseds.isEmpty()) {
            return energyUseds.get(0).getValue();
        }

        return 0;
    }

    @Override
    public List<EnergyUsed> dataStatisticsByCodes(List<String> indexCodes,
                                                  StatisticType statisticType, TimeType timeType,
                                                  Date beginTime, Date endTime) {
        if (indexCodes == null || indexCodes.isEmpty()) {
            return Collections.emptyList();
        }

        return periodDataMapper.dataStatisticsByCodes(
                indexCodes,
                statisticType,
                timeType,
                beginTime,
                endTime);
    }

    @Override
    public List<EnergyUsed> dataStatisticsByCodes(List<String> indexCodes,
                                                  StatisticType statisticType,
                                                  TimeType timeType,
                                                  Date beginTime, Date endTime,
                                                  boolean isFilter) {
        if (indexCodes == null || indexCodes.isEmpty()) {
            return Collections.emptyList();
        }

        return periodDataMapper.dataStatisticsFilterByCodes(
                indexCodes,
                statisticType,
                timeType,
                beginTime,
                endTime,
                isFilter);
    }

    @Override
    public StatisticResult statisticById(String indexId, TimeType timeType, Date dataTime) {
        if (StringUtils.isEmpty(indexId)) {
            return null;
        }

        List<String> indexIds = Collections.singletonList(indexId);
        List<StatisticResult> results = statisticById(indexIds, timeType, dataTime);
        if (!results.isEmpty()) {
            return results.get(0);
        }

        return new StatisticResult();
    }

    @Override
    public List<StatisticResult> statisticById(List<String> indexIds, TimeType timeType, Date dataTime) {
        if (indexIds == null || indexIds.isEmpty()) {
            return Collections.emptyList();
        }

        String timeCode = TimeManager.getTimeCode(dataTime, timeType);
        return statisticById(indexIds, timeType,
                TimeManager.getBeginTime(timeCode), TimeManager.getEndTime(timeCode));
    }

    @Override
    public List<StatisticResult> statisticById(String indexId, TimeType timeType,
                                               Date beginTime, Date endTime) {
        if (StringUtils.isEmpty(indexId)) {
            return Collections.emptyList();
        }

        List<String> indexIds = Collections.singletonList(indexId);
        return statisticById(indexIds, timeType, beginTime, endTime);
    }

    @Override
    public List<StatisticResult> statisticById(List<String> indexIds, TimeType timeType,
                                               Date beginTime, Date endTime) {
        if (indexIds == null || indexIds.isEmpty()) {
            return Collections.emptyList();
        }

        ReBuildTime reBuildTime = new ReBuildTime(timeType, beginTime, endTime).invoke();
        DateTime begin = reBuildTime.getBegin();
        DateTime end = reBuildTime.getEnd();

        DateTime lastYearOfBeginTime = begin.plusYears(-1);
        DateTime lastYearOfEndTime = end.plusYears(-1);
        DateTime previousOfBeginTime = getTime(begin, timeType, -1, STATISTIC_QUERY_TYPE);
        DateTime previousOfEndTime = getTime(end, timeType, -1, STATISTIC_QUERY_TYPE);

        List<MeterPoint> aggregateIndexList = energyIndexService.listMeterPointByIds(indexIds);
        List<EnergyUsed> currentValues = periodDataMapper.statistic(indexIds,
                timeType,
                begin.toDate(),
                end.toDate());
        List<EnergyUsed> previousValues = periodDataMapper.statistic(indexIds,
                timeType,
                previousOfBeginTime.toDate(),
                previousOfEndTime.toDate());
        List<EnergyUsed> lastYearValues = periodDataMapper.statistic(indexIds,
                timeType,
                lastYearOfBeginTime.toDate(),
                lastYearOfEndTime.toDate());

        List<StatisticResult> statisticResults =
                periodDataMapper.getStatisticDatasById(indexIds, timeType);

        return buildNewDatas(aggregateIndexList, timeType, currentValues, previousValues,
                lastYearValues, statisticResults, begin, end, STATISTIC_QUERY_TYPE);
    }

    /**
     * 统计一段时间周期内指标每个数据时间的本期值、上期值
     * 这里是上期数值略有不同，小时的是昨天的，日数据是上个月的，月是去年的
     *
     * @param indexIds  指标主键
     * @param timeType  时间周期类型
     * @param beginTime 开始时间
     * @param endTime   结束时间
     * @return 统计结果
     */
    @Override
    public List<StatisticResult> statisticOtherCycle(List<String> indexIds, TimeType timeType, Date beginTime, Date endTime) {
        if (indexIds == null || indexIds.isEmpty()) {
            return Collections.emptyList();
        }
        String type = "other";
        ReBuildTime reBuildTime = new ReBuildTime(timeType, beginTime, endTime).invoke();
        DateTime begin = reBuildTime.getBegin();
        DateTime end = reBuildTime.getEnd();
        DateTime previousOfBeginTime = getTime(begin, timeType, -1, type);
        DateTime previousOfEndTime = getTime(end, timeType, -1, type);
        List<MeterPoint> aggregateIndexList = energyIndexService.listMeterPointByIds(indexIds);
        List<EnergyUsed> currentValues = periodDataMapper.statistic(indexIds,
                timeType,
                begin.toDate(),
                end.toDate());
        List<EnergyUsed> previousValues = periodDataMapper.statistic(indexIds,
                timeType,
                previousOfBeginTime.toDate(),
                previousOfEndTime.toDate());
        return buildNewDatas(aggregateIndexList, timeType, currentValues, previousValues,
                new ArrayList<>(), null, begin, end, type);
    }

    @Override
    public StatisticResult statisticByCode(String indexCode,
                                           TimeType timeType, Date dataTime) {
        if (StringUtils.isEmpty(indexCode)) {
            return null;
        }

        List<String> indexCodes = Collections.singletonList(indexCode);
        List<StatisticResult> results = statisticByCode(indexCodes, timeType, dataTime);
        if (!results.isEmpty()) {
            return results.get(0);
        }

        return new StatisticResult();
    }

    @Override
    public List<StatisticResult> statisticByCode(List<String> indexCodes,
                                                 TimeType timeType, Date dataTime) {
        if (indexCodes == null || indexCodes.isEmpty()
        ) {
            return Collections.emptyList();
        }

        String timeCode = TimeManager.getTimeCode(dataTime, timeType);
        return statisticByCode(indexCodes, timeType,
                TimeManager.getBeginTime(timeCode), TimeManager.getEndTime(timeCode));
    }

    @Override
    public List<StatisticResult> statisticByCode(String indexCode, TimeType timeType,
                                                 Date beginTime, Date endTime) {
        if (StringUtils.isEmpty(indexCode)) {
            return Collections.emptyList();
        }

        List<String> indexCodes = Collections.singletonList(indexCode);
        return statisticByCode(indexCodes, timeType, beginTime, endTime);
    }

    private final static String STATISTIC_QUERY_TYPE = "normal";

    @Override
    public List<StatisticResult> statisticByCode(List<String> indexCodes,
                                                 TimeType timeType, Date beginTime, Date endTime) {
        if (indexCodes == null || indexCodes.isEmpty()) {
            return Collections.emptyList();
        }

        ReBuildTime reBuildTime = new ReBuildTime(timeType, beginTime, endTime).invoke();
        DateTime begin = reBuildTime.getBegin();
        DateTime end = reBuildTime.getEnd();

        List<MeterPoint> aggregateIndexList =
                energyIndexService.listMeterPointByCodes(indexCodes);
        DateTime lastYearOfBeginTime = begin.plusYears(-1);
        DateTime lastYearOfEndTime = end.plusYears(-1);
        DateTime previousOfBeginTime = getTime(begin, timeType, -1, STATISTIC_QUERY_TYPE);
        DateTime previousOfEndTime = getTime(end, timeType, -1, STATISTIC_QUERY_TYPE);

        List<EnergyUsed> currentValues = periodDataMapper.statisticByCode(
                indexCodes,
                timeType,
                begin.toDate(),
                end.toDate());
        List<EnergyUsed> previousValues = periodDataMapper.statisticByCode(
                indexCodes,
                timeType,
                previousOfBeginTime.toDate(),
                previousOfEndTime.toDate());
        List<EnergyUsed> lastYearValues = periodDataMapper.statisticByCode(
                indexCodes,
                timeType,
                lastYearOfBeginTime.toDate(),
                lastYearOfEndTime.toDate());

        List<StatisticResult> statisticResults =
                periodDataMapper.getStatisticDatasByCode(indexCodes, timeType);

        return buildNewDatas(aggregateIndexList,
                timeType,
                currentValues,
                previousValues,
                lastYearValues,
                statisticResults, begin, end, STATISTIC_QUERY_TYPE);
    }

    @Override
    public EnergyUsed getDataByIndexCode(String indexCode, String timeCode) {
        return periodDataMapper.getDataByIndexCode(indexCode, timeCode);
    }

    private EnergyUsed findValue(List<EnergyUsed> items, String indexId, DateTime dataTime) {
        Optional<EnergyUsed> value = items.stream()
                .filter(f -> StringUtils.equalsIgnoreCase(f.getPointId(), indexId)
                        && dataTime.equals(new DateTime(f.getDataTime())))
                .findAny();
        return value.orElseGet(EnergyUsed::new);

    }

    private DateTime getTime(DateTime time, TimeType timeType, int offset, String type) {
        if (STATISTIC_QUERY_TYPE.equalsIgnoreCase(type)) {
            if (timeType == TimeType.HOUR) {
                return time.plusHours(offset);
            } else if (timeType == TimeType.DAY) {
                return time.plusDays(offset);
            } else if (timeType == TimeType.MONTH) {
                return time.plusMonths(offset);
            } else if (timeType == TimeType.YEAR) {
                return time.plusYears(offset);
            }
            return time;
        } else {
            switch (timeType) {
                case HOUR:
                    return time.plusDays(offset);
                case DAY:
                    return time.plusMonths(offset);
                case MONTH:
                case YEAR:
                    return time.plusYears(offset);
                default:
                    return time;
            }
        }
    }


    private List<StatisticResult> buildNewDatas(List<MeterPoint> indexList, TimeType timeType,
                                                List<EnergyUsed> currentValues,
                                                List<EnergyUsed> previousValues,
                                                List<EnergyUsed> lastYearValues,
                                                List<StatisticResult> statisticResults,
                                                DateTime beginTime,
                                                DateTime endTime, String type) {
        List<StatisticResult> results = new ArrayList<>();
        indexList.forEach(index -> {
            DateTime tmpTime = beginTime;
            String indexId = index.getPointId();
            Optional<StatisticResult> statisticResult;
            if (statisticResults == null) {
                statisticResult = Optional.empty();
            } else {
                statisticResult = statisticResults.stream()
                        .filter(f -> StringUtils.equalsIgnoreCase(f.getIndexId(), indexId))
                        .findFirst();
            }

            while (tmpTime.isBefore(endTime)) {
                StatisticResult result = new StatisticResult();
                result.setIndexId(indexId);
                result.setIndexCode(index.getCode());
                result.setIndexName(index.getName());
                result.setUnitId(index.getUnitId());

                if (statisticResult.isPresent()) {
                    result.setMaxValue(statisticResult.get().getMaxValue());
                    result.setMinValue(statisticResult.get().getMinValue());
                    result.setAvgValue(statisticResult.get().getAvgValue());
                }

                result.setDataTime(tmpTime.toDate());
                EnergyUsed currentValue = findValue(currentValues, indexId, tmpTime);
                result.setCurrentValue(currentValue.getValue() == null ? 0 : currentValue.getValue());

                DateTime preTime = getTime(tmpTime, timeType, -1, type);
                EnergyUsed previousValue = findValue(previousValues, indexId, preTime);
                result.setPreviousValue(previousValue.getValue() == null ? 0 : previousValue.getValue());

                DateTime lastYearTime = tmpTime.plusYears(-1);
                EnergyUsed lastYearValue = findValue(lastYearValues, indexId, lastYearTime);
                result.setLastYearValue(lastYearValue.getValue() == null ? 0 : lastYearValue.getValue());
                results.add(result);
                tmpTime = getTime(tmpTime, timeType, 1, STATISTIC_QUERY_TYPE);
            }
        });

        return results;
    }

    public static <E> List<List<E>> splitList(List<E> targetList, Integer splitSize) {
        if (targetList == null) {
            return Collections.emptyList();
        }

        int size = targetList.size();
        List<List<E>> resultList = new ArrayList<>();
        if (size <= splitSize) {
            resultList.add(targetList);
        } else {
            for (int i = 0; i < size; i += splitSize) {
                //用于限制最后一部分size小于splitSize的list
                int limit = i + splitSize;
                if (limit > size) {
                    limit = size;
                }
                resultList.add(targetList.subList(i, limit));
            }
        }
        return resultList;
    }

    private static class ReBuildTime {
        private TimeType timeType;
        private Date beginTime;
        private Date endTime;
        private DateTime begin;
        private DateTime end;

        public ReBuildTime(TimeType timeType, Date beginTime, Date endTime) {
            this.timeType = timeType;
            this.beginTime = beginTime;
            this.endTime = endTime;
        }

        public DateTime getBegin() {
            return begin;
        }

        public DateTime getEnd() {
            return end;
        }

        public ReBuildTime invoke() {
            begin = new DateTime(beginTime);
            end = new DateTime(endTime);
            DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:00:00");
            if (timeType == TimeType.HOUR) {
                formatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:00:00");
            } else if (timeType == TimeType.DAY) {
                formatter = DateTimeFormat.forPattern("yyyy-MM-dd");
            } else if (timeType == TimeType.MONTH) {
                formatter = DateTimeFormat.forPattern("yyyy-MM-01");
            } else if (timeType == TimeType.YEAR) {
                formatter = DateTimeFormat.forPattern("yyyy-01-01");
            }

            begin = formatter.parseDateTime(begin.toString(formatter));
            end = formatter.parseDateTime(end.toString(formatter));
            return this;
        }
    }
}
