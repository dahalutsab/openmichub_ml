/**
 * Chart components, exported as one array so a module can import the set.
 *
 * They are standalone so the dashboards can pull in only what they draw, but
 * in practice each board uses most of them, and importing them one by one in
 * three NgModules is three places to forget.
 */
import { AreaChartComponent } from './area-chart.component';
import { BarChartComponent } from './bar-chart.component';
import { DonutChartComponent } from './donut-chart.component';
import { HeatmapComponent } from './heatmap.component';
import { MetricTileComponent } from './metric-tile.component';
import { SparklineComponent } from './sparkline.component';

export * from './chart.types';
export {
  AreaChartComponent,
  BarChartComponent,
  DonutChartComponent,
  HeatmapComponent,
  MetricTileComponent,
  SparklineComponent,
};

export const OMH_CHARTS = [
  AreaChartComponent,
  BarChartComponent,
  DonutChartComponent,
  HeatmapComponent,
  MetricTileComponent,
  SparklineComponent,
] as const;
