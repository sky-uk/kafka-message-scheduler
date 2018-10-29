package com.sky.kms

import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty

package object kafka {
  type Topic = String Refined NonEmpty
}
