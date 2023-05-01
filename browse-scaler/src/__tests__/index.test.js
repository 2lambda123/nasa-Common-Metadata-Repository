const nock = require('nock');
const { handler } = require('../index');
const config = require('../config');

const collectionWithBrowse = require('./C179003030-ORNL_DAAC.json');
const collectionWithoutBrowse = require('./C1214587974-SCIOPS.json');

const granuleWithBrowse = require('./C1711961296-LPCLOUD_granules.json');
const granuleWithBrowseMultipleImages = require('./G1200460416-ESA.json');
const granuleWithoutBrowse = require('./C179003030-ORNL_DAAC_granules.json');

const invalidCollectionIdResponse = require('./C000000000-MISSING_NO.json');
const invalidGranuleIdResponse = require('./G000000000-MISSING_NO.json');

describe('handler functions', () => {
  describe('get collection(dataset) imagery', () => {
    nock(config.CMR_ROOT_URL)
      .get(/search\/collections\.json/)
      .reply(200, collectionWithBrowse);

    test('no errors', async () => {
      const res = await handler({
        path: '/datasets/C00000000-FOO',
        queryStringParameters: {
          h: '300',
          w: '400'
        }
      });
      expect(res).toHaveProperty('statusCode', 200);
    });
  });

  describe('get granule imagery', () => {
    nock(config.CMR_ROOT_URL)
      .get(/search\/collections\.json/)
      .reply(200, collectionWithoutBrowse)
      .get(/search\/granules\.json/)
      .reply(200, granuleWithBrowse);

    test('no errors', async () => {
      const res = await handler({
        path: '/granule/G00000000-FOO',
        queryStringParameters: {
          h: '1280',
          w: '1024'
        }
      });
      expect(res).toHaveProperty('statusCode', 200);
    });
  });

  describe('get granule imagery', () => {
    nock(config.CMR_ROOT_URL)
      .get(/search\/granules\.json/)
      .reply(200, granuleWithBrowseMultipleImages);

    test('multiple image urls', async () => {
      const res = await handler({
        path: '/granule/G1200460416-ESA',
        queryStringParameters: {
          h: '1280',
          w: '1024',
          imageSrc:
            'https://eoimages.gsfc.nasa.gov/images/imagerecords/151000/151261/atlanticbloom_amo_2023110_lrg.jpg'
        }
      });
      expect(res).toHaveProperty('statusCode', 200);
    });
  });
});
