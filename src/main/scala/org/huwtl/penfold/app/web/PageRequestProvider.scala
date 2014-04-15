package org.huwtl.penfold.app.web

import org.scalatra.Params
import org.huwtl.penfold.readstore.{PageReference, PageRequest}

trait PageRequestProvider {
  def parsePageRequestParams(params: Params, pageSize: Int) = {
    val pageReference = params.get("page")

    if (pageReference.isDefined) {
      PageRequest(pageSize, Some(PageReference(pageReference.get)))
    }
    else {
      PageRequest(pageSize)
    }
  }
}
