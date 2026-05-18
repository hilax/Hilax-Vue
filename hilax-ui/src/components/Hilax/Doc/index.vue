<template>
  <div>
    <svg-icon icon-class="question" @click="goto" />
  </div>
</template>

<script>
export default {
  name: 'HilaxDoc',
  data() {
    return {
      url: 'https://hilax.local/docs'
    }
  },
  methods: {
    goto() {
      window.open(this.url)
    }
  }
}
</script>