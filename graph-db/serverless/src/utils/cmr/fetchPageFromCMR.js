import fetch from 'node-fetch'
import 'array-foreach-async'

import { chunkArray } from '../chunkArray'

/**
 * Fetch a page of collections from CMR search endpoint and initiate or continue scroll request
 * @param {String} scrollId An optional scroll-id given from the CMR
 * @param {String} token An optional Echo Token
 * @param {Gremlin Traversal Object} gremlinConnection connection to gremlin server
 * @param {String} providerId CMR provider id whose collections to bootstrap, null means all providers.
 * @returns [{JSON}] An array of UMM JSON collection results
 */
export const fetchPageFromCMR = async ({
  scrollId,
  token,
  gremlinConnection,
  providerId,
  scrollNum = 0,
  sqs
}) => {
  const requestHeaders = {}

  console.log(`Fetch collections from CMR, scroll #${scrollNum}`)

  if (token) {
    requestHeaders['Echo-Token'] = token
  }

  if (scrollId) {
    requestHeaders['CMR-Scroll-Id'] = scrollId
  }

  let fetchUrl = `${process.env.CMR_ROOT}/search/collections.json?page_size=${process.env.PAGE_SIZE}&scroll=true`

  if (providerId !== null) {
    fetchUrl += `&provider=${providerId}`
  }

  try {
    const cmrCollections = await fetch(fetchUrl, {
      method: 'GET',
      headers: requestHeaders
    })

    const { 'cmr-scroll-id': cmrScrollId } = cmrCollections.headers.raw()

    const collectionsJson = await cmrCollections.json()

    const { feed = {} } = collectionsJson
    const { entry = [] } = feed

    const chunkedItems = chunkArray(entry, 10)

    if (chunkedItems.length !== 0) {
      await chunkedItems.forEachAsync(async (chunk) => {
        const sqsEntries = []
        chunk.forEach((collection) => {
          const { id: conceptId } = collection
          sqsEntries.push({
            Id: conceptId,
            MessageBody: JSON.stringify({
              action: 'concept-update',
              'concept-id': conceptId
            })
          })
        })

        await sqs.sendMessageBatch({
          QueueUrl: process.env.COLLECTION_INDEXING_QUEUE_URL,
          Entries: sqsEntries
        }).promise()
      })
    }
    // If we have an active scrollId and there are more results
    if (cmrScrollId && entry.length === parseInt(process.env.PAGE_SIZE, 10)) {
      await fetchPageFromCMR({
        scrollId: cmrScrollId,
        token,
        gremlinConnection,
        providerId,
        scrollNum: (scrollNum + 1),
        sqs
      })
    }
  } catch (e) {
    console.error(`Could not complete request due to error: ${e}`)
  }

  return scrollId
}
