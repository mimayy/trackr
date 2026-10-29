import { describe, expect, it } from 'vitest'
import { describeAccuracy, distanceMeters } from './geo'

describe('distanceMeters', () => {
  it('is zero for the same point', () => {
    expect(distanceMeters(14.5547, 121.0244, 14.5547, 121.0244)).toBeCloseTo(0, 5)
  })

  it('measures a short distance accurately', () => {
    // Roughly 111 m north of the Manila office pin.
    const metres = distanceMeters(14.5547, 121.0244, 14.5557, 121.0244)
    expect(metres).toBeGreaterThan(105)
    expect(metres).toBeLessThan(118)
  })

  it('measures the distance between the two branches', () => {
    // Manila to Cebu is roughly 570 km.
    const metres = distanceMeters(14.5547, 121.0244, 10.3157, 123.8854)
    expect(metres).toBeGreaterThan(560_000)
    expect(metres).toBeLessThan(590_000)
  })

  it('is symmetric', () => {
    const forward = distanceMeters(14.5547, 121.0244, 14.4795, 121.0198)
    const backward = distanceMeters(14.4795, 121.0198, 14.5547, 121.0244)
    expect(forward).toBeCloseTo(backward, 6)
  })
})

describe('describeAccuracy', () => {
  it('describes a reading in words rather than metres', () => {
    expect(describeAccuracy(8)).toBe('Very good')
    expect(describeAccuracy(30)).toBe('Good')
    expect(describeAccuracy(60)).toBe('Fair')
    expect(describeAccuracy(200)).toBe('Poor')
  })
})
