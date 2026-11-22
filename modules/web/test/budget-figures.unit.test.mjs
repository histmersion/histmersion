import assert from 'node:assert/strict';
import { test } from 'node:test';
import { compareFigure, compareRuns, floorOf, frameBound, leaf, median } from './budget-figures.mjs';

test('median of an odd and an even count', () => {
  assert.equal(median([3, 1, 2]), 2);
  assert.equal(median([4, 1, 3, 2]), 2.5);
});

test('a figure is named by its last part, a repeated test\'s number left out', () => {
  assert.equal(leaf('fullLoadZoom#2.longTaskMax'), 'longTaskMax');
  assert.equal(leaf('eventLayer.pan.droppedPct'), 'droppedPct');
  assert.ok(frameBound('library.clickMs') && !frameBound('clusterZoom.longTaskMax'));
});

test('floors: 20 ms for times, 3 MB of heap, 5 % of the DOM, none for counts that must not change', () => {
  assert.equal(floorOf('clusterZoom.longTaskMax', 100), 20);
  assert.equal(floorOf('memoryAfterLoad.heapMB', 30), 3);
  assert.equal(floorOf('memoryEventLayer.nodes', 8000), 400);
  assert.equal(floorOf('clusterZoom.notAnimated', 0), 0);
});

test('worse by more than the runs\' noise and the floor, every run of the change above every run of the base: a regression', () => {
  const r = compareFigure('clusterZoom.longTaskMax', [120, 130, 125], [190, 185, 200]);
  assert.equal(r.regression, true);
  assert.equal(r.worse, 65);
  assert.equal(r.noise, 15);
});

test('within the noise, overlapping runs, under the floor, or frame-bound: no regression', () => {
  assert.equal(compareFigure('clusterZoom.longTaskMax', [120, 200, 125], [190, 185, 210]).regression, false);
  assert.equal(compareFigure('clusterZoom.longTaskMax', [120, 130, 125], [135, 140, 138]).regression, false);
  assert.equal(compareFigure('clusterZoom.longTaskMax', [100, 101, 100], [110, 112, 111]).regression, false);
  assert.equal(compareFigure('eventLayer.pan.droppedPct', [10, 11, 12], [60, 61, 62]).regression, false);
  assert.equal(compareFigure('clusterZoom.longTaskMax', [120, 130, 125], [60, 70, 65]).regression, false);
});

test('a count that must not move: one more on every run is a regression', () => {
  assert.equal(compareFigure('clusterZoom.notAnimated', [0, 0, 0], [1, 1, 1]).regression, true);
  assert.equal(compareFigure('clusterZoom.notAnimated', [0, 0, 0], [0, 0, 0]).regression, false);
});

test('runs compared figure by figure; a figure on one side only is shown, not failed', () => {
  const rows = compareRuns([{ 'a.longTaskMax': 100, 'old.longTaskMax': 5 }, { 'a.longTaskMax': 105, 'old.longTaskMax': 6 }],
    [{ 'a.longTaskMax': 180, 'new.longTaskMax': 1 }, { 'a.longTaskMax': 185, 'new.longTaskMax': 2 }]);
  assert.deepEqual(rows.map(r => [r.key, r.regression, !!r.missing]),
    [['a.longTaskMax', true, false], ['new.longTaskMax', false, true], ['old.longTaskMax', false, true]]);
});
