import gremlin from 'gremlin'
import 'array-foreach-async'

import indexCampaign from './indexCampaign'
import indexPlatform from './indexPlatform'
import indexRelatedUrl from './indexRelatedUrl'
import { deleteCmrCollection } from './deleteCmrCollection'

const gremlinStatistics = gremlin.process.statics

/**
 * Given a collection from the CMR, index it into Gremlin
 * @param {JSON} collection collection object from `items` array in cmr response
 * @param {Gremlin Traversal Object} gremlinConnection connection to gremlin server
 * @returns
 */
export const indexCmrCollection = async (collection, gremlinConnection) => {
  const {
    meta: {
      'concept-id': conceptId
    },
    umm: {
      EntryTitle: entryTitle,
      DOI: {
        DOI: doiDescription
      },
      Projects: projects,
      Platforms: platforms,
      RelatedUrls: relatedUrls
    }
  } = collection

  // delete the collection first so that we can clean up its related documentation vertices
  await deleteCmrCollection(conceptId, gremlinConnection)

  let doiUrl = 'Not provided'
  let landingPage = `${process.env.CMR_ROOT}/concepts/${conceptId}.html`

  if (doiDescription) {
    // Take the second element from the split method
    const [, doiAddress] = doiDescription.split(':')
    doiUrl = `https://dx.doi.org/${doiAddress}`
    landingPage = doiUrl
  }

  let dataset = null
  try {
    // Use `fold` and `coalesce` to check existance of vertex, and create one if none exists.
    dataset = await gremlinConnection
      .V()
      .hasLabel('dataset')
      .has('concept-id', conceptId)
      .fold()
      .coalesce(
        gremlinStatistics.unfold(),
        gremlinConnection.addV('dataset')
          .property('landing-page', landingPage)
          .property('title', entryTitle)
          .property('concept-id', conceptId)
          .property('doi', doiDescription || 'Not provided')
      )
      .next()
  } catch (error) {
    console.error(`Error indexing collection [${conceptId}]: ${error.message}`)

    return false
  }

  const { value = {} } = dataset
  const { id: datasetId } = value

  if (projects && projects.length > 0) {
    await projects.forEachAsync(async (project) => {
      await indexCampaign(project, gremlinConnection, datasetId, conceptId)
    })
  }

  if (platforms && platforms.length > 0) {
    await platforms.forEachAsync(async (platform) => {
      await indexPlatform(platform, gremlinConnection, datasetId, conceptId)
    })
  }

  if (relatedUrls && relatedUrls.length > 0) {
    await relatedUrls.forEachAsync(async (relatedUrl) => {
      await indexRelatedUrl(relatedUrl, gremlinConnection, datasetId, conceptId)
    })
  }

  console.log(`Dataset vertex [${datasetId}] indexed for collection [${conceptId}]`)

  return true
}
